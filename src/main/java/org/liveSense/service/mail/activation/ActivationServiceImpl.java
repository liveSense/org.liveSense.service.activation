/*
 *  Copyright 2010 Robert Csakany <robson@semmi.se>.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.liveSense.service.mail.activation;

import java.lang.reflect.InvocationTargetException;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.wrapper.JcrNodeTransformer;
import org.liveSense.core.wrapper.JcrNodeWrapper;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(label="%service.name", 
			description="%service.description",
			immediate = true,
			metatype = true)
@Service(value=ActivationService.class)
@Properties(value={
		@Property(name=ActivationServiceImpl.PARAM_ACTIVATION_PATH,
				label="%activationPath.name",
				value=ActivationServiceImpl.DEFAULT_ACTIVATION_PATH),
		
		@Property(name=ActivationServiceImpl.PARAM_ACTIVATION_EXPIRE,
				label="%activationExpire.name",
				longValue=ActivationServiceImpl.DEFAULT_ACTIVATION_EXPIRE)
})
			
public class ActivationServiceImpl implements ActivationService {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(ActivationServiceImpl.class);

    public static final String PARAM_ACTIVATION_PATH = "activationPath";
    public static final String DEFAULT_ACTIVATION_PATH = "activation";

    public static final String PARAM_ACTIVATION_EXPIRE = "activationExpire";
    public static final long DEFAULT_ACTIVATION_EXPIRE = 60*60*24;
 
    private String activationPath = DEFAULT_ACTIVATION_PATH;
    private Long activationExpire = DEFAULT_ACTIVATION_EXPIRE;

    @Reference
    private SlingRepository repository;


            //
        String PARAM_CONTENT_PATH="";
        String[] DEFAULT_CONTENT_PATH={};

        String[] contentPath={};

    /**
     * Activates this component.
     *
     * @param componentContext The OSGi <code>ComponentContext</code> of this
     *            component.
    */
    @Activate
    protected void activate(ComponentContext componentContext) throws RepositoryException {
        Dictionary<?, ?> props = componentContext.getProperties();

        activationPath = OsgiUtil.toString(props.get(PARAM_ACTIVATION_PATH), DEFAULT_ACTIVATION_PATH);
        activationExpire = OsgiUtil.toLong(props.get(PARAM_ACTIVATION_EXPIRE), DEFAULT_ACTIVATION_EXPIRE);
  //      stopActivationSchedulerJob();
  //      startActivationSchedulerJob();

        // Checking activation folder exists
        // If doesn't we create it
        Session session = null;
        try {
        	session = repository.loginAdministrative(null);

	        if (activationPath.startsWith("/")) activationPath = activationPath.substring(1);
	        if (activationPath.endsWith("/")) activationPath = activationPath.substring(0, activationPath.length()-1);

	        String[] spool = activationPath.split("/");
	        Node node = session.getRootNode();
	        for (int i = 0; i < spool.length; i++) {
	            String name = spool[i];
	            if (!"".equals(name) && !node.hasNode(name)) {
	                node = node.addNode(name, "nt:unstructured");
	                node.setProperty("sling:resourceType", "liveSense/activationFolder");
	                log.info("Creating: {}",node.getPath());
	            } else {
	                if (!"".equals(name)) node = node.getNode(name);
	            }
	        }
	        if (session.hasPendingChanges()) {
	            session.save();
	        }
        } catch (RepositoryException e) {
        	log.error("Activate failed", e);
		} finally {
			if (session != null) session.logout();
		}
    }

    public void addActivationCode(Session session, final String activationCode) throws RepositoryException {
    	addActivationCode(session, null, activationCode, null);
    }

    public void addActivationCode(Session session, final String activationCode, @SuppressWarnings("rawtypes") Map fields) throws RepositoryException {
    	addActivationCode(session, null, activationCode, fields);
    }

    
    public void addActivationCode(Session session, String userName, final String activationCode) throws RepositoryException {
    	addActivationCode(session, userName, activationCode, null);
    }

    public void addActivationCode(Session session, String userName, final String activationCode, @SuppressWarnings("rawtypes") Map fields)
            throws RepositoryException {
        String prop = null;

        try {
			Node activationNode = session.getRootNode().getNode(activationPath).addNode(activationCode,"nt:unstructured");
            activationNode.setProperty("sling:resourceType", "liveSense/ActivationCode");
            if (userName != null) activationNode.setProperty("user", userName);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, activationExpire.intValue());
            activationNode.setProperty("expire", cal.getTimeInMillis());
            
            if (fields != null) {
            	try {
					JcrNodeTransformer.transformMapToNode(activationNode, fields, null);
				} catch (InstantiationException e) {
					log.error("Error converting map to node", e);
				} catch (IllegalAccessException e) {
					log.error("Error converting map to node", e);
				} catch (InvocationTargetException e) {
					log.error("Error converting map to node", e);
				} catch (NoSuchMethodException e) {
					log.error("Error converting map to node", e);
				}
            }
   
            if (session.hasPendingChanges()) {
                session.save();
            }

        } catch (RepositoryException e) {
            throw e;
        } finally {
        }
    }

    public JcrNodeWrapper getActivationFields(Session session, String activationCode) throws RepositoryException {
        try {
			if (!session.getRootNode().hasNode(activationPath))
				return null;
			if (!session.getRootNode().getNode(activationPath).hasNode(activationCode))
				return null;
			
			return new JcrNodeWrapper(session.getRootNode().getNode(activationPath).getNode(activationCode));
		} catch (RepositoryException e) {
            throw e;
        } finally {
        }
    }
    
    public boolean checkActivationCode(Session session, String userName, String activationCode)
            throws 
            RepositoryException {
        String prop = null;

        try {
			if (!session.getRootNode().hasNode(activationPath))
				return false;
			if (!session.getRootNode().getNode(activationPath).hasNode(activationCode))
				return false;
			if (!session.getRootNode().getNode(activationPath).getNode(activationCode).getProperty("user").getString().equals(userName))
				return false;
			return true;
		} catch (RepositoryException e) {
            throw e;
        } finally {
        }
    }

    public boolean checkActivationCode(Session session, String activationCode)
            throws 
            RepositoryException {
        String prop = null;

        try {
			if (!session.getRootNode().hasNode(activationPath))
				return false;
			if (!session.getRootNode().getNode(activationPath).hasNode(activationCode))
				return false;
			return true;
		} catch (RepositoryException e) {
            throw e;
        } finally {
        }
    }

    public boolean removeActivationCode(Session session, String activationCode)
            throws RepositoryException {
        String prop = null;

        try {
			if (!session.getRootNode().hasNode(activationPath)) {
				return false;
			}
			if (!session.getRootNode().getNode(activationPath).hasNode(activationCode)) {
				return false;
			} else {
				session.getRootNode().getNode(activationPath).getNode(activationCode).remove();
				return true;
			}

		} catch (RepositoryException e) {
            throw e;
        } finally {
        }
    }
}
