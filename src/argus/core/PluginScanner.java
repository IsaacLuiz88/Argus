package argus.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.util.ArrayList;
import java.util.List;

public class PluginScanner {

    public static List<String> scanPlugins() {
        List<String> plugins = new ArrayList<>();

        Bundle currentBundle = FrameworkUtil.getBundle(PluginScanner.class);
        
        if (currentBundle != null && currentBundle.getBundleContext() != null) {
        	Bundle[] bundles = currentBundle.getBundleContext().getBundles();

            for (Bundle bundle : bundles) {
                String name = bundle.getSymbolicName();
                String version = bundle.getVersion().toString();

                plugins.add(name + ":" + version);
            }
            for (String plugin : plugins) {
            System.out.println(plugin);
            }
        }
        return plugins;
    }
}
