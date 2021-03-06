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

import org.liveSense.misc.jcrWrapper.JcrNodeWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Map;

/**
 *
 * @author robson
 */
public interface ActivationService {

	public void addActivationCode(Session session, String activationCode) throws RepositoryException;
	public void addActivationCode(Session session, String activationCode, @SuppressWarnings("rawtypes") Map fields) throws RepositoryException;
	public void addActivationCode(Session session, String userName, String activationCode) throws RepositoryException;
	public void addActivationCode(Session session, String userName, String activationCode, @SuppressWarnings("rawtypes") Map fields) throws RepositoryException;
    public JcrNodeWrapper getActivationFields(Session session, String activationCode) throws RepositoryException;
	public boolean checkActivationCode(Session session, String userName, String activationCode) throws RepositoryException;
	public boolean checkActivationCode(Session session, String activationCode) throws RepositoryException;
	public boolean removeActivationCode(Session session, String activationCode) throws RepositoryException;
}
