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

import java.io.Serializable;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.liveSense.service.userManager.exceptions.PrincipalIsNotUserException;
import org.liveSense.service.userManager.exceptions.UserNotExistsException;
import org.liveSense.utils.AdministrativeService;
import org.liveSense.utils.GenericValue;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Robert Csakany (robson@semmi.se)
 * @created Feb 12, 2010
 */


/**
 * @scr.component label="%service.name"
 *                description="%service.description"
 *                immediate="true"
 * @scr.service
 * @
 */
public class ActivationImpl extends AdministrativeService implements Activation {

    /**
     * default log
     */
    private final Logger log = LoggerFactory.getLogger(ActivationImpl.class);


    /**
     * @scr.property    label="%activationPath.name"
     *                  description="%activationPath.description"
     *                  valueRef="DEFAULT_ACTIVATION_PATH"
     */
    public static final String PARAM_ACTIVATION_PATH = "activationPath";
    public static final String DEFAULT_ACTIVATION_PATH = "activation";
    private String activationPath = DEFAULT_ACTIVATION_PATH;


    /**
     * @scr.property    label="%activationExpire.name"
     *                  description="%activationExpire.description"
     *                  valueRef="DEFAULT_ACTIVATION_EXPIRE"
     */
    public static final String PARAM_ACTIVATION_EXPIRE = "activationExpire";
    public static final Long DEFAULT_ACTIVATION_EXPIRE = new Long(60*60*24);
    private Long activationExpire = DEFAULT_ACTIVATION_EXPIRE;
    

    /**
     * @scr.property    label="%activationPurgeJobPeriod.name"
     *                  description="%activationPurgeJobPeriod.description"
     *                  valueRef="DEFAULT_ACTIVATION_PURGE_JOB_PERIOD"
     */
    public static final String PARAM_ACTIVATION_PURGE_JOB_PERIOD = "activationPurgeJobPeriod";
    public static final Long DEFAULT_ACTIVATION_PURGE_JOB_PERIOD = new Long(5);
    private Long activationPurgeJobPeriod = DEFAULT_ACTIVATION_PURGE_JOB_PERIOD;
  

    /**
     * The JCR Repository we access to resolve resources
     *
     * @scr.reference
     */
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
    protected void activate(ComponentContext componentContext) {
        Dictionary<?, ?> props = componentContext.getProperties();

        // ACTIVATION PATH
        String activationPathNew = (String) componentContext.getProperties().get(PARAM_ACTIVATION_PATH);
        if (activationPathNew == null || activationPathNew.length() == 0) {
            activationPathNew = DEFAULT_ACTIVATION_PATH;
        }
        if (!activationPathNew.equals(this.activationPath)) {
            log.info("Setting new activationPath {} (was {})", activationPathNew, this.activationPath);
            this.activationPath = activationPathNew;
        }

        // ACTIVATION EXPIRE
        Long activationExpireNew = (Long) componentContext.getProperties().get(PARAM_ACTIVATION_EXPIRE);
        if (activationExpireNew == null || activationExpireNew == 0) {
            activationExpireNew = DEFAULT_ACTIVATION_EXPIRE;
        }
        if (!activationExpireNew.equals(this.activationExpire)) {
            log.info("Setting new activationExpire {} (was {})", activationExpireNew, this.activationExpire);
            this.activationExpire = activationExpireNew;
        }

        // ACTIVATION PURGE JOB PERIOD
        Long activationPurgeJobPeriodNew = (Long) componentContext.getProperties().get(PARAM_ACTIVATION_PURGE_JOB_PERIOD);
        if (activationPurgeJobPeriodNew == null || activationPurgeJobPeriodNew == 0) {
            activationPurgeJobPeriodNew = DEFAULT_ACTIVATION_PURGE_JOB_PERIOD;
        }
        if (!activationPurgeJobPeriodNew.equals(this.activationPurgeJobPeriod)) {
            log.info("Setting new activationPurgeJobPeriod {} (was {})", activationPurgeJobPeriodNew, this.activationPurgeJobPeriod);
            this.activationPurgeJobPeriod = activationPurgeJobPeriodNew;
            stopActivationSchedulerJob();
            startActivationSchedulerJob();
        } else {
            startActivationSchedulerJob();
        }

        // Checking activation folder exists
        // If doesn't we create it
        try {
            Session session = getAdministrativeSession(repository);
            if (!session.getRootNode().hasNode(activationPath)) {
                Node actNode = session.getRootNode().addNode(activationPath,"sling:Folder");
                actNode.setProperty("sling:resourceType", "liveSense/ActivationFolder");
                if (session.hasPendingChanges()) {
                    session.save();
                }
            }
            releaseAdministrativeSession(session);
        } catch (RepositoryException ex) {
            log.error("Could not create Activation Folder: /"+activationPath, ex);
        }

    }



    /**
    *   @scr.reference policy="static"
    *      interface="org.apache.sling.commons.scheduler.Scheduler"
    *      bind="bindScheduler"
    **/
    protected Scheduler scheduler;

    protected void bindScheduler(Scheduler scheduler) throws Exception {
        this.scheduler = scheduler;
    }


    public void startActivationSchedulerJob() {
        log.info("Starting activationPurgeJob");

        Map<String, Serializable> config = new HashMap<String, Serializable>();
        //set any configuration options in the config map here
        Job job = new ActivationPurgeJob(repository, activationPath);
        try {
            scheduler.addPeriodicJob("activationPurgeJob", job, config, activationPurgeJobPeriod, false);
        } catch(Throwable th) {
            log.error("Cannot start activationPurgeJob", th);
        }
    }

    public void stopActivationSchedulerJob() {
        log.info("Stopping activationPurgeJob");
        try {
            scheduler.removeJob("activationPurgeJob");
        } catch(Throwable th) {
            log.error("Cannot stop activationPurgeJob", th);
        }
    }

    public void addActivationCode(String userName, final String activationCode)
            throws UserNotExistsException, PrincipalIsNotUserException,
            RepositoryException {
        Session selfRegSession = null;
        String prop = null;

        try {
            selfRegSession = getAdministrativeSession(repository);

            UserManager userManager = AccessControlUtil.getUserManager(selfRegSession);
            Authorizable authorizable = userManager.getAuthorizable(userName);

            if (authorizable == null) {
                // user already exists!
                throw new UserNotExistsException(
                        "User does not exists: "
                        + userName);
            } else {
                if (authorizable.isGroup()) {
                    throw new PrincipalIsNotUserException("Principal is not user: " + userName);
                }
                User user = (User) authorizable;

                Node activationNode = selfRegSession.getRootNode().getNode(activationPath).addNode(userName,"nt:unstructured");
                activationNode.setProperty("sling:resourceType", "liveSense/ActivationCode");
                activationNode.setProperty("user", userName);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, activationExpire.intValue());
                activationNode.setProperty("expire", cal.getTimeInMillis());
                //user.setProperty("activation_code", GenericValue.getGenericValueFromObject(activationCode).get(1));
                user.setProperty("status", GenericValue.getGenericValueFromObject("PENDING").get(1));

                if (selfRegSession.hasPendingChanges()) {
                    selfRegSession.save();
                }

            }
        } catch (RepositoryException e) {
            throw e;
        } finally {
            releaseAdministrativeSession(selfRegSession);
        }
    }

    public String getActivationCode(String userName)
            throws UserNotExistsException, PrincipalIsNotUserException,
            RepositoryException {
        Session selfRegSession = null;
        String prop = null;

        try {
            selfRegSession = getAdministrativeSession(repository);

            UserManager userManager = AccessControlUtil.getUserManager(selfRegSession);
            Authorizable authorizable = userManager.getAuthorizable(userName);

            if (authorizable == null) {
                // user already exists!
                throw new UserNotExistsException(
                        "User does not exists: "
                        + userName);
            } else {
                if (authorizable.isGroup()) {
                    throw new PrincipalIsNotUserException("Principal is not user: " + userName);
                }
                User user = (User) authorizable;
                if (user.hasProperty("activation_code")) {
                    prop = user.getProperty("activation_code")[0].getString();
                }
            }
        } catch (RepositoryException e) {
            throw e;
        } finally {
            releaseAdministrativeSession(selfRegSession);
        }
        return prop;
    }

    public String removeActivationCode(String userName)
            throws UserNotExistsException, PrincipalIsNotUserException,
            RepositoryException {
        Session selfRegSession = null;
        String prop = null;

        try {
            selfRegSession = getAdministrativeSession(repository);

            UserManager userManager = AccessControlUtil.getUserManager(selfRegSession);
            Authorizable authorizable = userManager.getAuthorizable(userName);

            if (authorizable == null) {
                // user already exists!
                throw new UserNotExistsException(
                        "User does not exists: "
                        + userName);
            } else {
                if (authorizable.isGroup()) {
                    throw new PrincipalIsNotUserException("Principal is not user: " + userName);
                }
                User user = (User) authorizable;
                if (user.hasProperty("activation_code")) {
                    user.setProperty("activation_code", (Value) null);
                }
                if (selfRegSession.hasPendingChanges()) {
                    selfRegSession.save();
                }

            }
        } catch (RepositoryException e) {
            throw e;
        } finally {
            releaseAdministrativeSession(selfRegSession);
        }
        return prop;
    }

}
