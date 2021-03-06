/*
 * Copyright 2006-2008 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.enunciate.modules.jersey;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Context resolver for JAXB.
 *
 * @author Ryan Heaton
 */
@Provider
public class EnunciateJAXBContextResolver implements ContextResolver<JAXBContext> {

  private static final Logger LOG = Logger.getLogger(EnunciateJAXBContextResolver.class.getName());

  private final JAXBContext context;
  private final Object prefixMapper;

  private final Set<Class> types;

  public EnunciateJAXBContextResolver() throws Exception {
    this.types = loadTypes();
    this.prefixMapper = loadPrefixMapper();

    JAXBContext context = JAXBContext.newInstance(this.types.toArray(new Class[types.size()]));
    if (this.prefixMapper != null) {
      context = new DelegatingJAXBContext(context) {
        @Override
        public Marshaller createMarshaller() throws JAXBException {
          Marshaller marshaller = super.createMarshaller();
          try {
            marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", prefixMapper);
          }
          catch (PropertyException e) {
            //fall through...
          }
          return marshaller;
        }
      };
    }
    this.context = context;
  }

  public Set<Class> loadTypes() {
    HashSet<Class> types = new HashSet<Class>();
    InputStream stream = loadResource("/jaxrs-jaxb-types.list");
    if (stream != null) {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        String line = reader.readLine();
        while (line != null) {
          try {
            types.add(loadClass(line));
          }
          catch (Throwable e) {
            LOG.log(Level.SEVERE, "Error loading jaxb type for jersey.", e);
          }
          line = reader.readLine();
        }
      }
      catch (Throwable e) {
        LOG.log(Level.SEVERE, "Error reading jaxb types for jersey.", e);
      }
    }


    return types;
  }

  protected Object loadPrefixMapper() {
    InputStream stream = loadResource("/ns2prefix.properties");
    Object prefixMapper = null;
    if (stream != null) {
      try {
        //we want to support a prefix mapper, but don't want to break those on JDK 6 that don't have the prefix mapper on the classpath.
        Properties ns2prefix = new Properties();
        ns2prefix.load(stream);
        prefixMapper = Class.forName("org.codehaus.enunciate.modules.jersey.PrefixMapper").getConstructor(Properties.class).newInstance(ns2prefix);
      }
      catch (Throwable e) {
        prefixMapper = null;
      }
    }
    return prefixMapper;
  }

  /**
   * Return the default ClassLoader to use: typically the thread context
   * ClassLoader, if available; the ClassLoader that loaded the EnunciateJAXBContextResolver
   * class will be used as fallback.
   * <p>Call this method if you intend to use the thread context ClassLoader
   * in a scenario where you absolutely need a non-null ClassLoader reference:
   * for example, for class path resource loading (but not necessarily for
   * <code>Class.forName</code>, which accepts a <code>null</code> ClassLoader
   * reference as well).
   * @return the default ClassLoader (never <code>null</code>)
   * @see java.lang.Thread#getContextClassLoader()
   */
  public static ClassLoader getDefaultClassLoader() {
    ClassLoader cl = null;
    try {
      cl = Thread.currentThread().getContextClassLoader();
    }
    catch (Throwable ex) {
      //fall through...
    }
    if (cl == null) {
      // No thread context class loader -> use class loader of this class.
      cl = EnunciateJAXBContextResolver.class.getClassLoader();
    }
    return cl;
  }

  /**
   * Loads a resource from the classpath.
   *
   * @param resource The resource to load.
   * @return The resource.
   */
  protected InputStream loadResource(String resource) {
    return getDefaultClassLoader().getResourceAsStream(resource);
  }

  /**
   * Loads a class from the classpath.
   *
   * @param classname The class name.
   * @return The class.
   */
  protected Class loadClass(String classname) throws ClassNotFoundException {
    return getDefaultClassLoader().loadClass(classname);
  }

  public JAXBContext getContext(Class<?> objectType) {
    if (types.contains(objectType)) {
      return context;
    }
    else if (objectType.isAnnotationPresent(XmlRootElement.class)) {
      //if this is a root element, we'll do our best to apply our namespace prefix mapper.
      try {
        JAXBContext context = JAXBContext.newInstance(objectType);
        if (this.prefixMapper != null) {
          context = new DelegatingJAXBContext(context) {
            @Override
            public Marshaller createMarshaller() throws JAXBException {
              Marshaller marshaller = super.createMarshaller();
              try {
                marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", prefixMapper);
              }
              catch (PropertyException e) {
                //fall through...
              }
              return marshaller;
            }
          };
        }
        return context;
      }
      catch (Exception e) {
        //fall through...
      }
    }

    return null;
  }

}
