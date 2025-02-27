package com.vaadin.hilla.route;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.VaadinRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouteData;
import com.vaadin.flow.router.RouteParameterData;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.MenuAccessControl;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import com.vaadin.flow.server.menu.AvailableViewInfo;
import com.vaadin.flow.server.menu.RouteParamType;
import com.vaadin.hilla.route.records.ClientViewConfig;

import static org.mockito.ArgumentMatchers.any;

public class RouteUnifyingIndexHtmlRequestListenerTest {

    protected static final String SCRIPT_STRING = RouteUnifyingIndexHtmlRequestListener.SCRIPT_STRING
            .replace("%s;", "");

    private final ClientRouteRegistry clientRouteRegistry = Mockito
            .mock(ClientRouteRegistry.class);
    private RouteUnifyingIndexHtmlRequestListener requestListener;
    private IndexHtmlResponse indexHtmlResponse;
    private VaadinService vaadinService;
    private VaadinRequest vaadinRequest;
    private DeploymentConfiguration deploymentConfiguration;
    private RouteUtil routeUtil;

    @Rule
    public TemporaryFolder projectRoot = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        vaadinService = Mockito.mock(VaadinService.class);
        deploymentConfiguration = Mockito.mock(DeploymentConfiguration.class);
        Mockito.when(vaadinService.getDeploymentConfiguration())
                .thenReturn(deploymentConfiguration);
        Mockito.when(clientRouteRegistry.getAllRoutes())
                .thenReturn(prepareClientRoutes());
        routeUtil = new RouteUtil(clientRouteRegistry);
        requestListener = new RouteUnifyingIndexHtmlRequestListener(
                clientRouteRegistry, deploymentConfiguration, routeUtil, null,
                null, true);

        indexHtmlResponse = Mockito.mock(IndexHtmlResponse.class);
        vaadinRequest = Mockito.mock(VaadinRequest.class);
        Mockito.when(indexHtmlResponse.getVaadinRequest())
                .thenReturn(vaadinRequest);
        var userPrincipal = Mockito.mock(Principal.class);
        Mockito.when(vaadinRequest.getUserPrincipal())
                .thenReturn(userPrincipal);

        final Document document = Mockito.mock(Document.class);
        final Element element = new Element("head");
        Mockito.when(document.head()).thenReturn(element);
        Mockito.when(indexHtmlResponse.getDocument()).thenReturn(document);

        final RouteRegistry serverRouteRegistry = Mockito
                .mock(RouteRegistry.class);
        final List<RouteData> flowRegisteredRoutes = prepareServerRoutes();
        Mockito.when(serverRouteRegistry.getRegisteredRoutes())
                .thenReturn(flowRegisteredRoutes);
        Mockito.when(serverRouteRegistry
                .getRegisteredAccessibleMenuRoutes(any(), any()))
                .thenReturn(flowRegisteredRoutes);

        final Router router = Mockito.mock(Router.class);
        Mockito.when(vaadinService.getRouter()).thenReturn(router);
        Mockito.when(router.getRegistry()).thenReturn(serverRouteRegistry);

        final Map<String, ClientViewConfig> clientRoutes = prepareClientRoutes();
        Mockito.when(clientRouteRegistry.getAllRoutes())
                .thenReturn(clientRoutes);

        var instantiator = Mockito.mock(Instantiator.class);
        var menuAccessControl = Mockito.mock(MenuAccessControl.class);
        Mockito.when(vaadinService.getInstantiator()).thenReturn(instantiator);
        Mockito.when(instantiator.getMenuAccessControl())
                .thenReturn(menuAccessControl);
        Mockito.when(menuAccessControl.getPopulateClientSideMenu())
                .thenReturn(MenuAccessControl.PopulateClientMenu.ALWAYS);
    }

    private Map<String, ClientViewConfig> prepareClientRoutes() {
        final var routes = new LinkedHashMap<String, ClientViewConfig>();

        var homeConfig = new ClientViewConfig();
        homeConfig.setTitle("Home");
        homeConfig.setRolesAllowed(null);
        homeConfig.setLoginRequired(false);
        homeConfig.setRoute("/home");
        homeConfig.setLazy(false);
        homeConfig.setAutoRegistered(false);
        homeConfig.setMenu(null);
        homeConfig.setChildren(null);
        homeConfig.setRouteParameters(Collections.emptyMap());
        routes.put("/home", homeConfig);

        var profileConfig = new ClientViewConfig();
        profileConfig.setTitle("Profile");
        profileConfig
                .setRolesAllowed(new String[] { "ROLE_USER", "ROLE_ADMIN" });
        profileConfig.setLoginRequired(true);
        profileConfig.setRoute("/profile");
        profileConfig.setLazy(false);
        profileConfig.setAutoRegistered(false);
        profileConfig.setMenu(null);
        profileConfig.setChildren(null);
        profileConfig.setRouteParameters(Collections.emptyMap());
        routes.put("/profile", profileConfig);

        var userProfileConfig = new ClientViewConfig();
        userProfileConfig.setTitle("User Profile");
        userProfileConfig.setRolesAllowed(new String[] { "ROLE_ADMIN" });
        userProfileConfig.setLoginRequired(true);
        userProfileConfig.setRoute("/user/:userId");
        userProfileConfig.setLazy(false);
        userProfileConfig.setAutoRegistered(false);
        userProfileConfig.setMenu(null);
        userProfileConfig.setChildren(null);
        userProfileConfig
                .setRouteParameters(Map.of(":userId", RouteParamType.REQUIRED));
        routes.put("/user/:userId", userProfileConfig);

        ClientViewConfig rootIndexConfig = new ClientViewConfig();
        rootIndexConfig.setTitle("Index");
        rootIndexConfig.setRolesAllowed(null);
        rootIndexConfig.setLoginRequired(false);
        rootIndexConfig.setRoute("");
        rootIndexConfig.setLazy(false);
        rootIndexConfig.setAutoRegistered(false);
        rootIndexConfig.setMenu(null);
        rootIndexConfig.setChildren(Collections.emptyList());
        rootIndexConfig.setRouteParameters(Collections.emptyMap());
        routes.put("/", rootIndexConfig);

        ClientViewConfig ordersIndexConfig = new ClientViewConfig();
        ordersIndexConfig.setTitle("Orders");
        ordersIndexConfig.setRolesAllowed(null);
        ordersIndexConfig.setLoginRequired(false);
        ordersIndexConfig.setRoute("");
        ordersIndexConfig.setLazy(false);
        ordersIndexConfig.setAutoRegistered(false);
        ordersIndexConfig.setMenu(null);
        ordersIndexConfig.setChildren(Collections.emptyList());
        ordersIndexConfig.setRouteParameters(Collections.emptyMap());
        routes.put("/orders", ordersIndexConfig);

        return routes;
    }

    private static List<RouteData> prepareServerRoutes() {
        final List<RouteData> flowRegisteredRoutes = new ArrayList<>();
        final RouteData bar = new RouteData(Collections.emptyList(), "bar",
                Collections.emptyList(), Component.class,
                Collections.emptyList());
        flowRegisteredRoutes.add(bar);

        final RouteData foo = new RouteData(Collections.emptyList(), "foo",
                Collections.emptyList(), RouteTarget.class,
                Collections.emptyList());
        flowRegisteredRoutes.add(foo);

        final RouteData wildcard = new RouteData(Collections.emptyList(),
                "wildcard/:___wildcard*",
                Map.of("___wildcard",
                        new RouteParameterData(":___wildcard*", null)),
                RouteTarget.class, Collections.emptyList());
        flowRegisteredRoutes.add(wildcard);

        final RouteData editUser = new RouteData(Collections.emptyList(),
                "/:___userId/edit",
                Map.of("___userId", new RouteParameterData(":___userId", null)),
                RouteTarget.class, Collections.emptyList());
        flowRegisteredRoutes.add(editUser);

        final RouteData comments = new RouteData(Collections.emptyList(),
                "comments/:___commentId?",
                Map.of("___commentId",
                        new RouteParameterData(":___commentId?", null)),
                RouteTarget.class, Collections.emptyList());
        flowRegisteredRoutes.add(comments);
        return flowRegisteredRoutes;
    }

    @Test
    public void when_productionMode_anonymous_user_should_modifyIndexHtmlResponse_with_anonymously_allowed_routes()
            throws IOException {
        try (MockedStatic<VaadinService> mocked = Mockito
                .mockStatic(VaadinService.class)) {
            mocked.when(VaadinService::getCurrent).thenReturn(vaadinService);
            Mockito.when(deploymentConfiguration.isProductionMode())
                    .thenReturn(true);
            Mockito.when(vaadinRequest.getUserPrincipal()).thenReturn(null);

            requestListener.modifyIndexHtmlResponse(indexHtmlResponse);
        }
        Mockito.verify(indexHtmlResponse, Mockito.times(1)).getDocument();
        MatcherAssert.assertThat(
                indexHtmlResponse.getDocument().head().select("script"),
                Matchers.notNullValue());

        DataNode script = indexHtmlResponse.getDocument().head()
                .select("script").dataNodes().get(0);

        final String scriptText = script.getWholeData();
        MatcherAssert.assertThat(scriptText,
                Matchers.startsWith(SCRIPT_STRING));

        final String views = scriptText.substring(SCRIPT_STRING.length());

        final var mapper = new ObjectMapper();

        var actual = mapper.readTree(views);
        var expected = mapper.readTree(getClass().getResource(
                "/META-INF/VAADIN/available-views-anonymous.json"));

        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void when_productionMode_authenticated_user_should_modifyIndexHtmlResponse_with_user_allowed_routes()
            throws IOException {
        try (MockedStatic<VaadinService> mocked = Mockito
                .mockStatic(VaadinService.class)) {
            mocked.when(VaadinService::getCurrent).thenReturn(vaadinService);
            Mockito.when(deploymentConfiguration.isProductionMode())
                    .thenReturn(true);
            Mockito.when(vaadinRequest.isUserInRole("ROLE_USER"))
                    .thenReturn(true);

            requestListener.modifyIndexHtmlResponse(indexHtmlResponse);
        }
        Mockito.verify(indexHtmlResponse, Mockito.times(1)).getDocument();
        MatcherAssert.assertThat(
                indexHtmlResponse.getDocument().head().select("script"),
                Matchers.notNullValue());

        DataNode script = indexHtmlResponse.getDocument().head()
                .select("script").dataNodes().get(0);

        final String scriptText = script.getWholeData();
        MatcherAssert.assertThat(scriptText,
                Matchers.startsWith(SCRIPT_STRING));

        final String views = scriptText.substring(SCRIPT_STRING.length());

        final var mapper = new ObjectMapper();

        var actual = mapper.readTree(views);
        var expected = mapper.readTree(getClass()
                .getResource("/META-INF/VAADIN/available-views-user.json"));

        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void when_productionMode_admin_user_should_modifyIndexHtmlResponse_with_anonymous_and_admin_allowed_routes()
            throws IOException {
        try (MockedStatic<VaadinService> mocked = Mockito
                .mockStatic(VaadinService.class)) {
            mocked.when(VaadinService::getCurrent).thenReturn(vaadinService);
            Mockito.when(deploymentConfiguration.isProductionMode())
                    .thenReturn(true);
            Mockito.when(vaadinRequest.isUserInRole("ROLE_ADMIN"))
                    .thenReturn(true);
            requestListener.modifyIndexHtmlResponse(indexHtmlResponse);
        }
        Mockito.verify(indexHtmlResponse, Mockito.times(1)).getDocument();
        MatcherAssert.assertThat(
                indexHtmlResponse.getDocument().head().select("script"),
                Matchers.notNullValue());

        DataNode script = indexHtmlResponse.getDocument().head()
                .select("script").dataNodes().get(0);

        final String scriptText = script.getWholeData();
        MatcherAssert.assertThat(scriptText,
                Matchers.startsWith(SCRIPT_STRING));

        final String views = scriptText.substring(SCRIPT_STRING.length());

        final var mapper = new ObjectMapper();

        var actual = mapper.readTree(views);
        var expected = mapper.readTree(getClass()
                .getResource("/META-INF/VAADIN/available-views-admin.json"));

        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void when_developmentMode_should_modifyIndexHtmlResponse()
            throws IOException {
        try (MockedStatic<VaadinService> mocked = Mockito
                .mockStatic(VaadinService.class)) {
            mocked.when(VaadinService::getCurrent).thenReturn(vaadinService);
            Mockito.when(vaadinRequest.isUserInRole(Mockito.anyString()))
                    .thenReturn(true);
            mockDevelopmentMode();
            requestListener.modifyIndexHtmlResponse(indexHtmlResponse);
        }
        Mockito.verify(indexHtmlResponse, Mockito.times(1)).getDocument();
        MatcherAssert.assertThat(
                indexHtmlResponse.getDocument().head().select("script"),
                Matchers.notNullValue());

        DataNode script = indexHtmlResponse.getDocument().head()
                .select("script").dataNodes().get(0);

        final String scriptText = script.getWholeData();
        MatcherAssert.assertThat(scriptText,
                Matchers.startsWith(SCRIPT_STRING));

        final String views = scriptText.substring(SCRIPT_STRING.length());

        final var mapper = new ObjectMapper();

        var actual = mapper.readTree(views);
        var expected = mapper.readTree(getClass()
                .getResource("/META-INF/VAADIN/available-views-admin.json"));

        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void should_collectServerViews() {
        final Map<String, AvailableViewInfo> views;

        try (MockedStatic<VaadinService> mocked = Mockito
                .mockStatic(VaadinService.class)) {
            mocked.when(VaadinService::getCurrent).thenReturn(vaadinService);

            views = requestListener.collectServerViews();
        }
        MatcherAssert.assertThat(views, Matchers.aMapWithSize(4));
        MatcherAssert.assertThat(views.get("/bar").title(),
                Matchers.is("Component"));
        MatcherAssert.assertThat(views.get("/foo").title(),
                Matchers.is("RouteTarget"));
        MatcherAssert.assertThat(views.get("/bar").route(),
                Matchers.is("/bar"));
        MatcherAssert.assertThat(views.get("/wildcard").route(),
                Matchers.is("/wildcard"));
        MatcherAssert.assertThat(views.get("/comments").route(),
                Matchers.is("/comments"));
    }

    @Test
    public void when_productionMode_should_collectClientViews() {
        Mockito.when(deploymentConfiguration.isProductionMode())
                .thenReturn(true);
        Predicate<? super String> isUserInRole = role -> true;
        boolean isAuthenticated = true;
        var views = requestListener.collectClientViews(isUserInRole,
                isAuthenticated);
        MatcherAssert.assertThat(views, Matchers.aMapWithSize(4));
    }

    @Test
    public void when_developmentMode_should_collectClientViews()
            throws IOException {
        mockDevelopmentMode();
        boolean isUserAuthenticated = true;
        Predicate<? super String> isUserInRole = role -> true;
        var views = requestListener.collectClientViews(isUserInRole,
                isUserAuthenticated);
        MatcherAssert.assertThat(views, Matchers.aMapWithSize(4));
    }

    @Test
    public void when_exposeServerRoutesToClient_false_serverSideRoutesAreNotInResponse()
            throws IOException {
        Mockito.when(deploymentConfiguration.isProductionMode())
                .thenReturn(true);
        Mockito.when(vaadinRequest.isUserInRole(Mockito.anyString()))
                .thenReturn(true);
        var requestListener = new RouteUnifyingIndexHtmlRequestListener(
                clientRouteRegistry, deploymentConfiguration, routeUtil, null,
                null, false);

        requestListener.modifyIndexHtmlResponse(indexHtmlResponse);

        MatcherAssert.assertThat(
                indexHtmlResponse.getDocument().head().select("script"),
                Matchers.notNullValue());

        DataNode script = indexHtmlResponse.getDocument().head()
                .select("script").dataNodes().get(0);

        final String scriptText = script.getWholeData();
        MatcherAssert.assertThat(scriptText,
                Matchers.startsWith(SCRIPT_STRING));

        final String views = scriptText.substring(SCRIPT_STRING.length());

        final var mapper = new ObjectMapper();

        var actual = mapper.readTree(views);
        var expected = mapper.readTree(getClass()
                .getResource("/META-INF/VAADIN/only-client-views.json"));

        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    private void mockDevelopmentMode() throws IOException {
        Mockito.when(deploymentConfiguration.isProductionMode())
                .thenReturn(false);
        var frontendGeneratedDir = projectRoot.newFolder("frontend/generated");
        Mockito.when(deploymentConfiguration.getFrontendFolder())
                .thenReturn(frontendGeneratedDir.getParentFile());
    }

    @PageTitle("RouteTarget")
    private static class RouteTarget extends Component {
    }
}
