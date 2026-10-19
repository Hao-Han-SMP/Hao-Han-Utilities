package vn.haohan.utilities.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerMenuServiceTest {

    @Test
    @DisplayName("ServerLinksManager correctly populates website and discord links")
    void testServerLinksManagerPopulatesLinks() {
        DummyServerLinks dummyServerLinks = new DummyServerLinks();
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getServerLinks".equals(method.getName())) {
                        return dummyServerLinks;
                    }
                    return null;
                }
        );

        ServerLinksManager manager = new ServerLinksManager(mockServer);
        ServerLinks result = manager.createLinks();

        assertNotNull(result);
        assertEquals(URI.create(ServerLinksManager.DEFAULT_WEBSITE_URL), result.getLink(ServerLinks.Type.WEBSITE).getUrl());
        assertEquals(URI.create(ServerLinksManager.DEFAULT_DISCORD_URL), result.getLink(ServerLinks.Type.COMMUNITY).getUrl());
    }

    @Test
    @DisplayName("ServerMenuService manages state and prototype defaults")
    void testServerMenuServiceState() {
        DummyServerLinks dummyServerLinks = new DummyServerLinks();
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> "getServerLinks".equals(method.getName()) ? dummyServerLinks : null
        );

        Plugin mockPlugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );

        ServerLinksManager manager = new ServerLinksManager(mockServer);
        ServerMenuService service = new ServerMenuService(mockPlugin, manager);

        UUID playerId = UUID.randomUUID();
        assertFalse(service.isDiscordLinked(playerId));
        assertTrue(service.isShaderEnabled(playerId));
        assertEquals(32.0f, service.getViewRadius(playerId));

        service.setDiscordLinked(playerId, true);
        assertTrue(service.isDiscordLinked(playerId));
    }

    // Lightweight implementation of ServerLinks for Unit Testing without external mock libraries
    private static class DummyServerLinks implements ServerLinks {
        private final List<ServerLink> links = new ArrayList<>();

        @Override
        public ServerLink getLink(Type type) {
            return links.stream().filter(l -> l.getType() == type).findFirst().orElse(null);
        }

        @Override
        public List<ServerLink> getLinks() {
            return List.copyOf(links);
        }

        @Override
        public ServerLink setLink(Type type, URI url) {
            links.removeIf(l -> l.getType() == type);
            return addLink(type, url);
        }

        @Override
        public ServerLink addLink(Type type, URI url) {
            ServerLink link = new DummyServerLink(type, Component.text(type.name()), url);
            links.add(link);
            return link;
        }

        @Override
        public ServerLink addLink(Component displayName, URI url) {
            ServerLink link = new DummyServerLink(null, displayName, url);
            links.add(link);
            return link;
        }

        @Override
        @Deprecated
        public ServerLink addLink(String displayName, URI url) {
            return addLink(Component.text(displayName), url);
        }

        @Override
        public boolean removeLink(ServerLink link) {
            return links.remove(link);
        }

        @Override
        public ServerLinks copy() {
            DummyServerLinks copy = new DummyServerLinks();
            copy.links.addAll(this.links);
            return copy;
        }
    }

    private record DummyServerLink(ServerLinks.Type type, Component displayName, URI url) implements ServerLinks.ServerLink {
        @Override
        public ServerLinks.Type getType() {
            return type;
        }

        @Override
        public Component displayName() {
            return displayName;
        }

        @Override
        @Deprecated
        public String getDisplayName() {
            return displayName.toString();
        }

        @Override
        public URI getUrl() {
            return url;
        }
    }
}
