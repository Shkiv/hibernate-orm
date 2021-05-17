/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.mapping.lazytoone.mappedby;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.bytecode.enhance.spi.interceptor.BytecodeLazyAttributeInterceptor;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.bytecode.enhance.spi.interceptor.LazyAttributeLoadingInterceptor;
import org.hibernate.bytecode.spi.BytecodeEnhancementMetadata;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.persister.entity.EntityPersister;

import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.bytecode.enhancement.EnhancementOptions;
import org.hibernate.testing.jdbc.SQLStatementInterceptor;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static javax.persistence.FetchType.LAZY;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hibernate.annotations.FetchMode.JOIN;

/**
 * @author Steve Ebersole
 */
@RunWith( BytecodeEnhancerRunner.class)
@EnhancementOptions( lazyLoading = true )
public class JoinFetchedInverseToOneAllowProxyTests extends BaseNonConfigCoreFunctionalTestCase {
	private SQLStatementInterceptor sqlStatementInterceptor;

	@Override
	protected void applyMetadataSources(MetadataSources sources) {
		super.applyMetadataSources( sources );
		sources.addAnnotatedClass( Customer.class );
		sources.addAnnotatedClass( SupplementalInfo.class );
	}

	@Override
	protected void configureStandardServiceRegistryBuilder(StandardServiceRegistryBuilder ssrb) {
		super.configureStandardServiceRegistryBuilder( ssrb );
		ssrb.applySetting( AvailableSettings.ALLOW_ENHANCEMENT_AS_PROXY, true );
		sqlStatementInterceptor = new SQLStatementInterceptor( ssrb );
	}

	@Test
	public void testOwnerIsProxy() {
		sqlStatementInterceptor.clear();

		final EntityPersister supplementalInfoDescriptor = sessionFactory().getMetamodel().entityPersister( SupplementalInfo.class );
		final BytecodeEnhancementMetadata supplementalInfoEnhancementMetadata = supplementalInfoDescriptor.getBytecodeEnhancementMetadata();
		assertThat( supplementalInfoEnhancementMetadata.isEnhancedForLazyLoading(), is( true ) );

		final EntityPersister customerDescriptor = sessionFactory().getMetamodel().entityPersister( Customer.class );
		final BytecodeEnhancementMetadata customerEnhancementMetadata = customerDescriptor.getBytecodeEnhancementMetadata();
		assertThat( customerEnhancementMetadata.isEnhancedForLazyLoading(), is( true ) );

		inTransaction(
				(session) -> {

					// Get a reference to the SupplementalInfo we created

					final SupplementalInfo supplementalInfo = session.byId( SupplementalInfo.class ).getReference( 1 );

					// 1) we should have just the uninitialized SupplementalInfo enhanced proxy
					assertThat( sqlStatementInterceptor.getSqlQueries().size(), is( 0 ) );
					final BytecodeLazyAttributeInterceptor initialInterceptor = supplementalInfoEnhancementMetadata.extractLazyInterceptor( supplementalInfo );
					assertThat( initialInterceptor, instanceOf( EnhancementAsProxyLazinessInterceptor.class ) );

					// 2) Access the SupplementalInfo's id value - should trigger no SQL

					supplementalInfo.getId();
					assertThat( sqlStatementInterceptor.getSqlQueries().size(), is( 0 ) );
					assertThat( initialInterceptor, sameInstance( supplementalInfoEnhancementMetadata.extractLazyInterceptor( supplementalInfo ) ) );

					// 3) Access SupplementalInfo's `something` state
					//		- should trigger loading the "base group" state
					//		- customer should be join fetched as part of loading this base state
					supplementalInfo.getSomething();
					assertThat( sqlStatementInterceptor.getSqlQueries().size(), is( 1 ) );
					final BytecodeLazyAttributeInterceptor interceptor = supplementalInfoEnhancementMetadata.extractLazyInterceptor( supplementalInfo );
					assertThat( initialInterceptor, not( sameInstance( interceptor ) ) );
					assertThat( interceptor, instanceOf( LazyAttributeLoadingInterceptor.class ) );

					// 4) Access SupplementalInfo's `customer` state
					final Customer customer = supplementalInfo.getCustomer();
					assertThat( sqlStatementInterceptor.getSqlQueries().size(), is( 1 ) );

					customer.getId();
					customer.getName();
					assertThat( sqlStatementInterceptor.getSqlQueries().size(), is( 1 ) );
				}
		);
	}

	@Before
	public void createTestData() {
		inTransaction(
				(session) -> {
					final Customer customer = new Customer( 1, "Acme Brick" );
					session.persist( customer );
					final SupplementalInfo supplementalInfo = new SupplementalInfo( 1, customer, "extra details" );
					session.persist( supplementalInfo );
				}
		);
	}

	@After
	public void dropTestData() {
		inTransaction(
				(session) -> {
					session.createQuery( "delete Customer" ).executeUpdate();
					session.createQuery( "delete SupplementalInfo" ).executeUpdate();
				}
		);
	}

	@Entity( name = "Customer" )
	@Table( name = "customer" )
	public static class Customer {
		@Id
		private Integer id;
		private String name;
		@OneToOne( fetch = LAZY )
		private SupplementalInfo supplementalInfo;

		public Customer() {
		}

		public Customer(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		public Integer getId() {
			return id;
		}

		private void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public SupplementalInfo getSupplementalInfo() {
			return supplementalInfo;
		}

		public void setSupplementalInfo(SupplementalInfo supplementalInfo) {
			this.supplementalInfo = supplementalInfo;
		}
	}

	@Entity( name = "SupplementalInfo" )
	@Table( name = "supplemental" )
	public static class SupplementalInfo {
		@Id
		private Integer id;

		@OneToOne( fetch = LAZY, mappedBy = "supplementalInfo", optional = false )
//		@LazyToOne( value = NO_PROXY )
		@Fetch( JOIN )
		private Customer customer;

		private String something;

		public SupplementalInfo() {
		}

		public SupplementalInfo(Integer id, Customer customer, String something) {
			this.id = id;
			this.customer = customer;
			this.something = something;

			customer.setSupplementalInfo( this );
		}

		public Integer getId() {
			return id;
		}

		private void setId(Integer id) {
			this.id = id;
		}

		public Customer getCustomer() {
			return customer;
		}

		public void setCustomer(Customer customer) {
			this.customer = customer;
		}

		public String getSomething() {
			return something;
		}

		public void setSomething(String something) {
			this.something = something;
		}
	}
}
