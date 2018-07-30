/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.userguide.mapping.generated;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;
import org.hibernate.tuple.AnnotationValueGeneration;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.ValueGenerator;

import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;

/**
 * @author Vlad Mihalcea
 */
public class DatabaseValueGenerationTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
			Event.class
		};
	}

	@Test
	public void test() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			Event dateEvent = new Event( );
			entityManager.persist( dateEvent );
		} );
	}

	//tag::mapping-database-generated-value-example[]
	@Entity(name = "Event")
	public static class Event {

		@Id
		@GeneratedValue
		private Long id;

		@Column(name = "`timestamp`")
		@FunctionCreationTimestamp
		private Date timestamp;

		//Constructors, getters and setters are omitted for brevity
	//end::mapping-database-generated-value-example[]

		public Event() {}

		public Long getId() {
			return id;
		}

		public Date getTimestamp() {
			return timestamp;
		}
	//tag::mapping-database-generated-value-example[]
	}
	//end::mapping-database-generated-value-example[]

	//tag::mapping-database-generated-value-example[]

	@ValueGenerationType(generatedBy = FunctionCreationValueGeneration.class)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface FunctionCreationTimestamp {}

	public static class FunctionCreationValueGeneration
			implements AnnotationValueGeneration<FunctionCreationTimestamp> {

		@Override
		public void initialize(FunctionCreationTimestamp annotation, Class<?> propertyType) {
		}

		/**
		 * Generate value on INSERT
		 * @return when to generate the value
		 */
		public GenerationTiming getGenerationTiming() {
			return GenerationTiming.INSERT;
		}

		/**
		 * Returns null because the value is generated by the database.
		 * @return null
		 */
		public ValueGenerator<?> getValueGenerator() {
			return null;
		}

		/**
		 * Returns true because the value is generated by the database.
		 * @return true
		 */
		public boolean referenceColumnInSql() {
			return true;
		}

		/**
		 * Returns the database-generated value
		 * @return database-generated value
		 */
		public String getDatabaseGeneratedReferencedColumnValue() {
			return "current_timestamp";
		}
	}
	//end::mapping-database-generated-value-example[]
}
