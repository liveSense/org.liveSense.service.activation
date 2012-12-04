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

import java.util.Dictionary;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label="%activationPeriodicalPurgeJob.name",
	description="%activationPeriodicalPurgeJob.description",
	immediate=true,
	metatype=true,
	policy=ConfigurationPolicy.OPTIONAL)
@Service(value=java.lang.Runnable.class)
@Properties(value = {
	@Property(
			name="scheduler.name", 
			value="ActivationPeriodicalPurgeJob"),
	@Property(
			name="scheduler.expression", 
			value="0 0 * ? * * "),
	@Property(name=ActivationServiceImpl.PARAM_ACTIVATION_PATH,
			label="%activationPath.name",
			value=ActivationServiceImpl.DEFAULT_ACTIVATION_PATH)
})
public class ActivationPeriodicalPurgeJob implements Runnable {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(ActivationPeriodicalPurgeJob.class);

	@Reference(cardinality=ReferenceCardinality.MANDATORY_UNARY, policy=ReferencePolicy.DYNAMIC)
    private SlingRepository repository;

    String activationPath;

    @Activate
    protected void activate(ComponentContext componentContext) throws RepositoryException {
        Dictionary<?, ?> props = componentContext.getProperties();

        activationPath = OsgiUtil.toString(props.get(ActivationServiceImpl.PARAM_ACTIVATION_PATH), ActivationServiceImpl.DEFAULT_ACTIVATION_PATH);
    }

    public void run() {
        Session session = null;
    	try {
            session = repository.loginAdministrative(null);
            
            // Search for expired Activations
            String query = 
                    "/jcr:root/"+activationPath+"//element(*,nt:unstructured)[@sling:resourceType = 'liveSense/Activation' and @expire<"+System.currentTimeMillis()+"]";
            Query q;
            NodeIterator nodes=null;
            QueryManager qm;

            qm=session.getWorkspace().getQueryManager();
            q=qm.createQuery(query, javax.jcr.query.Query.XPATH);
            nodes = q.execute().getNodes();

            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                log.info("Removing expired activation: "+node.getName());
                node.remove();
            }

            if (session.hasPendingChanges()) {
                session.save();
            }
            
        } catch (RepositoryException ex) {
            log.error("Repository error in ActivationPurgeJob ",ex);
        } finally {
        	if (session != null)
        		session.logout();
        }
    }

}
