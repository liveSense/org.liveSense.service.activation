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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.api.SlingRepository;
import org.liveSense.core.AdministrativeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 13, 2010
 */
public class ActivationPurgeJob extends AdministrativeService implements Job {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(ActivationPurgeJob.class);

    private SlingRepository repository;

    String activationPath;

    public ActivationPurgeJob(SlingRepository repository, String activationPath) {
        this.activationPath = activationPath;
        this.repository = repository;
    }

    public void execute(JobContext context) {
        try {
            Session session = getAdministrativeSession(repository);
            
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
            
            releaseAdministrativeSession(session);

        } catch (RepositoryException ex) {
            log.error("Repository error in ActivationPurgeJob ",ex);
        }
    }

}
