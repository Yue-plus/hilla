/*
 * Copyright 2000-2024 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.hilla.route;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.nodes.DataNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.MenuAccessControl;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.ViewAccessChecker;
import com.vaadin.flow.server.communication.IndexHtmlRequestListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.MenuRegistry;
import com.vaadin.flow.server.menu.RouteParamType;
import com.vaadin.hilla.route.records.ClientViewConfig;

/**
 * Index HTML request listener for collecting the client side and the server
 * side views and adding them to index.html response.
 */
public class RouteUnifyingIndexHtmlRequestListener
        implements IndexHtmlRequestListener {
    protected static final String SCRIPT_STRING = """
            window.Vaadin = window.Vaadin ?? {};
            window.Vaadin.views = %s;""";

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RouteUnifyingIndexHtmlRequestListener.class);
    private final NavigationAccessControl accessControl;
    private final ClientRouteRegistry clientRouteRegistry;
    private final DeploymentConfiguration deploymentConfiguration;
    private final boolean exposeServerRoutesToClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RouteUtil routeUtil;
    private final ViewAccessChecker viewAccessChecker;

    /**
     * Creates a new listener instance with the given route registry.
     *
     * @param clientRouteRegistry
     *            the client route registry for getting the client side views
     * @param deploymentConfiguration
     *            the runtime deployment configuration
     * @param routeUtil
     *            the ClientRouteRegistry aware utility for checking if user is
     *            allowed to access a route
     * @param exposeServerRoutesToClient
     *            whether to expose server routes to the client
     */
    public RouteUnifyingIndexHtmlRequestListener(
            ClientRouteRegistry clientRouteRegistry,
            DeploymentConfiguration deploymentConfiguration,
            RouteUtil routeUtil,
            @Nullable NavigationAccessControl accessControl,
            @Nullable ViewAccessChecker viewAccessChecker,
            boolean exposeServerRoutesToClient) {
        this.clientRouteRegistry = clientRouteRegistry;
        this.deploymentConfiguration = deploymentConfiguration;
        this.routeUtil = routeUtil;
        this.accessControl = accessControl;
        this.viewAccessChecker = viewAccessChecker;
        this.exposeServerRoutesToClient = exposeServerRoutesToClient;

        mapper.addMixIn(AvailableViewInfo.class, IgnoreMixin.class);
    }

    @Override
    public void modifyIndexHtmlResponse(IndexHtmlResponse response) {
        final boolean isUserAuthenticated = response.getVaadinRequest()
                .getUserPrincipal() != null;
        final Map<String, AvailableViewInfo> availableViews = new HashMap<>(
                collectClientViews(response.getVaadinRequest()::isUserInRole,
                        isUserAuthenticated));
        if (exposeServerRoutesToClient) {
            LOGGER.debug(
                    "Exposing server-side views to the client based on user configuration");
            availableViews.putAll(collectServerViews());
        }

        if (availableViews.isEmpty()) {
            LOGGER.debug(
                    "No server-side nor client-side views found, skipping response modification.");
            return;
        }
        try {
            final String fileRoutesJson = mapper
                    .writeValueAsString(availableViews);
            final String script = SCRIPT_STRING.formatted(fileRoutesJson);
            response.getDocument().head().appendElement("script")
                    .appendChild(new DataNode(script));
        } catch (IOException e) {
            LOGGER.error(
                    "Failure while to write client and server routes to index html response",
                    e);
        }
    }

    protected Map<String, AvailableViewInfo> collectClientViews(
            Predicate<? super String> isUserInRole,
            boolean isUserAuthenticated) {
        if (!deploymentConfiguration.isProductionMode()) {
            clientRouteRegistry.loadLatestDevModeFileRoutesJsonIfNeeded(
                    deploymentConfiguration);
        }

        return clientRouteRegistry.getAllRoutes().entrySet().stream()
                .filter(viewMapping -> !hasRequiredParameter(
                        viewMapping.getValue().getRouteParameters()))
                .filter(viewMapping -> routeUtil.isRouteAllowed(isUserInRole,
                        isUserAuthenticated, viewMapping.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        viewMapping -> toAvailableViewInfo(
                                viewMapping.getValue())));
    }

    private boolean hasRequiredParameter(
            Map<String, RouteParamType> routeParameters) {
        return routeParameters != null && !routeParameters.isEmpty()
                && routeParameters.values().stream().anyMatch(
                        paramType -> paramType == RouteParamType.REQUIRED);
    }

    private AvailableViewInfo toAvailableViewInfo(ClientViewConfig config) {
        return new AvailableViewInfo(config.getTitle(),
                config.getRolesAllowed(), config.isLoginRequired(),
                config.getRoute(), config.isLazy(), config.isAutoRegistered(),
                config.menu(), Collections.emptyList(),
                config.getRouteParameters());
    }

    protected Map<String, AvailableViewInfo> collectServerViews() {
        final var vaadinService = VaadinService.getCurrent();
        if (vaadinService == null) {
            LOGGER.debug(
                    "No VaadinService found, skipping server view collection");
            return Collections.emptyMap();
        }
        final var serverRouteRegistry = vaadinService.getRouter().getRegistry();

        var accessControls = Stream.of(accessControl, viewAccessChecker)
                .filter(Objects::nonNull).toList();

        var serverRoutes = new HashMap<String, AvailableViewInfo>();

        if (vaadinService.getInstantiator().getMenuAccessControl()
                .getPopulateClientSideMenu() == MenuAccessControl.PopulateClientMenu.ALWAYS
                || clientRouteRegistry.hasMainLayout()) {
            MenuRegistry.collectAndAddServerMenuItems(
                    RouteConfiguration.forRegistry(serverRouteRegistry),
                    accessControls, serverRoutes);
        }

        return serverRoutes.values().stream()
                .filter(view -> view.routeParameters().values().stream()
                        .noneMatch(param -> param == RouteParamType.REQUIRED))
                .collect(Collectors.toMap(this::getMenuLink,
                        Function.identity()));
    }

    /**
     * Gets menu link with omitted route parameters.
     *
     * @param info
     *            the menu item's target view
     * @return target path for menu link
     */
    private String getMenuLink(AvailableViewInfo info) {
        final var parameterNames = info.routeParameters().keySet();
        return Stream.of(info.route().split("/"))
                .filter(Predicate.not(parameterNames::contains))
                .collect(Collectors.joining("/"));
    }

    /**
     * Mixin to ignore unwanted fields in the json results.
     */
    abstract static class IgnoreMixin {
        @JsonIgnore
        abstract List<AvailableViewInfo> children(); // we don't need it!
    }
}
