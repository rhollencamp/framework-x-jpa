/**
 * Framework X - Java Web Application Framework
 * Copyright (C) 2011 Robert Hollencamp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.frameworkx.plugins;

import com.frameworkx.AbstractApplication;
import com.frameworkx.Plugin;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JPA Persistence Plugin
 *
 * @author Robert Hollencamp
 */
public class JPA implements Plugin
{
	private static final ThreadLocal<EntityManager> entityManager = new ThreadLocal<EntityManager>();

	/**
	 * When the application is initialized, create the entity manager factory
	 *
	 * @param app
	 */
	public void init(final String name, final AbstractApplication app)
	{
		final Properties jpaProperties = new Properties();

		// get overrides from config
		String key = "plugin." + name + ".config.";
		String value;
		if ((value = app.getProperty(key + "driver")) != null) {
			jpaProperties.setProperty("javax.persistence.jdbc.driver", value);
		}
		if ((value = app.getProperty(key + "url")) != null) {
			jpaProperties.setProperty("javax.persistence.jdbc.url", value);
		}
		if ((value = app.getProperty(key + "user")) != null) {
			jpaProperties.setProperty("javax.persistence.jdbc.user", value);
		}
		if ((value = app.getProperty(key + "password")) != null) {
			jpaProperties.setProperty("javax.persistence.jdbc.password", value);
		}

		// create entity manager factory
		value = app.getProperty(key + "persistenceUnit");
		if (value == null || value.isEmpty()) {
			throw new IllegalStateException("Persistence Unit not specified in plugin config");
		}
		EntityManagerFactory emf = Persistence.createEntityManagerFactory(
				value,
				jpaProperties);
		app.getServletContext().setAttribute(JPA.class.getName(), emf);
	}

	/**
	 * Create an entity manager for this thread when a request is received\
	 *
	 * @param request
	 * @param response
	 */
	public void onRequestReceived(final HttpServletRequest request, final HttpServletResponse response)
	{
		// if this thread already has an entity manager, roll it back
		EntityManager em;
		if ((em = entityManager.get()) != null) {
			em.getTransaction().rollback();
			em.close();
		}

		// get the entity manager factory
		EntityManagerFactory emf = (EntityManagerFactory) request.getServletContext().getAttribute(JPA.class.getName());

		// create an entity manager
		em = emf.createEntityManager();
		em.getTransaction().begin();
		entityManager.set(em);
	}

	/**
	 * When a request is over, commit the transaction and close up the entity manager
	 *
	 * @param request
	 * @param response
	 */
	public void onRequestFinally(HttpServletRequest request, HttpServletResponse response)
	{
		EntityManager em = entityManager.get();

		try {
			EntityTransaction t = em.getTransaction();
			if (t.isActive()) {
				if (t.getRollbackOnly()) {
					t.rollback();
				} else {
					t.commit();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		} finally {
			em.close();
			entityManager.remove();
		}
	}

	/**
	 * Convenience function to persist an object to the entity manager
	 *
	 * @param o Entity to persist
	 */
	public static void persist(final Object o) {
		entityManager.get().persist(o);
	}

	/**
	 * Convenience function to get the active transaction
	 *
	 * @return Current Transaction
	 */
	public static EntityTransaction getTransaction() {
		return entityManager.get().getTransaction();
	}

	/**
	 * Accessor for the EntityManager for the current thread
	 *
	 * @return
	 */
	public static EntityManager getEntityManager() {
		return entityManager.get();
	}
}
