/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.services.web;

import org.jasig.cas.authentication.principal.SimpleWebApplicationServiceImpl;
import org.jasig.cas.services.RegexRegisteredService;
import org.jasig.cas.services.RegisteredService;
import org.jasig.cas.services.ServicesManager;
import org.jasig.cas.web.view.JsonViewUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MultiActionController to handle the deletion of RegisteredServices as well as
 * displaying them on the Manage Services page.
 *
 * @author Scott Battaglia
 * @since 3.1
 */
@Controller
public final class ManageRegisteredServicesMultiActionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManageRegisteredServicesMultiActionController.class);

    /** Ajax request header name to examine for exceptions. */
    private static final String AJAX_REQUEST_HEADER_NAME = "x-requested-with";

    /** Ajax request header value to examine for exceptions. */
    private static final String AJAX_REQUEST_HEADER_VALUE = "XMLHttpRequest";

    /** Instance of ServicesManager. */
    @NotNull
    private final ServicesManager servicesManager;

    @NotNull
    private final String defaultServiceUrl;

    /**
     * Instantiates a new manage registered services multi action controller.
     *
     * @param servicesManager the services manager
     * @param defaultServiceUrl the default service url
     */
    @Autowired
    public ManageRegisteredServicesMultiActionController(final ServicesManager servicesManager,
            @Value("${cas-management.securityContext.serviceProperties.service}") final String defaultServiceUrl) {
        this.servicesManager = servicesManager;
        this.defaultServiceUrl = defaultServiceUrl;
    }

    /**
     * Ensure default service exists.
     */
    private void ensureDefaultServiceExists() {
        final Collection<RegisteredService> c = this.servicesManager.getAllServices();
        if (c == null) {
            throw new IllegalStateException("Services cannot be empty");
        }

        if (!this.servicesManager.matchesExistingService(
                new SimpleWebApplicationServiceImpl(this.defaultServiceUrl))) {
            final RegexRegisteredService svc = new RegexRegisteredService();
            svc.setServiceId(defaultServiceUrl);
            svc.setName("Services Management Web Application");
            this.servicesManager.save(svc);
        }
    }
    /**
     * Authorization failure handling. Simply returns the view name.
     *
     * @return the view name.
     */
    @RequestMapping(value="authorizationFailure.html", method={RequestMethod.GET})
    public String authorizationFailureView() {
        return "authorizationFailure";
    }

    /**
     * Logout handling. Simply returns the view name.
     *
     * @param request the request
     * @param session the session
     * @return the view name.
     */
    @RequestMapping(value="logout.html", method={RequestMethod.GET})
    public String logoutView(final HttpServletRequest request, final HttpSession session) {
        LOGGER.debug("Invalidating application session...");
        session.invalidate();
        return "logout";
    }

    /**
     * Method to delete the RegisteredService by its ID. Will make sure
     * the default service that is the management app itself cannot be deleted
     * or the user will be locked out.
     *
     * @param idAsLong the id
     * @param response the response
     */
    @RequestMapping(value="deleteRegisteredService.html", method={RequestMethod.POST})
    public void deleteRegisteredService(@RequestParam("id") final long idAsLong,
                                        final HttpServletResponse response) {
        final RegisteredService r = this.servicesManager.delete(idAsLong);
        if (r == null) {
            throw new IllegalArgumentException("Service id " + idAsLong + " cannot be found.");
        }
        ensureDefaultServiceExists();
        final Map<String, Object> model = new HashMap<>();
        model.put("serviceName", r.getName());
        JsonViewUtils.render(model, response);
    }

    /**
     * Method to show the RegisteredServices.
     * @param response the response
     * @return the Model and View to go to after the services are loaded.
     */
    @RequestMapping(value="manage.html", method={RequestMethod.GET})
    public ModelAndView manage(final HttpServletResponse response) {
        ensureDefaultServiceExists();
        final Map<String, Object> model = new HashMap<>();
        model.put("defaultServiceUrl", this.defaultServiceUrl);
        return new ModelAndView("manage", model);
    }

    /**
     * Gets services.
     *
     * @param response the response
     */
    @RequestMapping(value="getServices.html", method={RequestMethod.GET})
    public void getServices(final HttpServletResponse response) {
        ensureDefaultServiceExists();
        final Map<String, Object> model = new HashMap<>();
        final List<RegisteredServiceBean> serviceBeans = new ArrayList<>();
        final List<RegisteredService> services = new ArrayList<>(this.servicesManager.getAllServices());
        for (final RegisteredService svc : services) {
            serviceBeans.add(RegisteredServiceBean.fromRegisteredService(svc));
        }
        model.put("services", serviceBeans);
        JsonViewUtils.render(model, response);
    }

    /**
     * Updates the {@link RegisteredService#getEvaluationOrder()}.
     *
     * @param id the service ids, whose order also determines the service evaluation order
     */
    @RequestMapping(value="updateRegisteredServiceEvaluationOrder.html", method={RequestMethod.POST})
    public void updateRegisteredServiceEvaluationOrder(@RequestParam("id") final long... id) {
        if (id == null || id.length == 0) {
            throw new IllegalArgumentException("No service id was received. Re-examine the request");
        }
        for (int i = 0; i < id.length; i++) {
            final long svcId = id[i];
            final RegisteredService svc = this.servicesManager.findServiceBy(svcId);
            if (svc == null) {
                throw new IllegalArgumentException("Service id " + svcId + " cannot be found.");
            }
            svc.setEvaluationOrder(i);
            this.servicesManager.save(svc);
        }
    }

    /**
     * Resolve exception.
     *
     * @param request the request
     * @param response the response
     * @param ex the ex
     */
    @ExceptionHandler
    public void resolveException(final HttpServletRequest request, final HttpServletResponse response,
                                 final Exception ex) {

        LOGGER.error(ex.getMessage(), ex);
        final String contentType = request.getHeader(this.AJAX_REQUEST_HEADER_NAME);
        if (contentType != null && contentType.equals(this.AJAX_REQUEST_HEADER_VALUE)) {
            LOGGER.debug("Handling exception {} for ajax request indicated by header {}",
                    ex.getClass().getName(), this.AJAX_REQUEST_HEADER_NAME);
            JsonViewUtils.renderException(ex, response);
        } else {
            LOGGER.trace("Unable to resolve exception {} for request. Ajax request header {} not found.",
                    ex.getClass().getName(), this.AJAX_REQUEST_HEADER_NAME);
        }
    }
}
