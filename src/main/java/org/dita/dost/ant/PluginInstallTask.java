/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2018 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */
package org.dita.dost.ant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.taskdefs.Get;
import org.apache.tools.zip.ZipUtil;
import org.dita.dost.platform.Registry;
import org.dita.dost.platform.Registry.Dependency;
import org.dita.dost.platform.SemVer;
import org.dita.dost.util.Configuration;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PluginInstallTask extends Task {

    private List<String> registries;

    private File tempDir;
    private String pluginFile;
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init() {
        registries = Arrays.asList(Configuration.configuration.get("registry").trim().split("\\s+"));
        try {
            tempDir = Files.createTempDirectory(null).toFile();
        } catch (IOException e) {
            throw new BuildException("Failed to create temporary directory: " + e.getMessage(), e);
        }
    }

    private void cleanUp() {
        if (tempDir != null) {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                throw new BuildException(e);
            }
        }
    }

    private URL asAbsoluteUrl() {
        try {
            final URI uri = new URI(pluginFile);
            if (uri.isAbsolute()) {
                return uri.toURL();
            }
        } catch (MalformedURLException | URISyntaxException e) {}
        return null;
    }

    @Override
    public void execute() throws BuildException {
        if (pluginFile == null) {
            throw new BuildException("pluginName argument not set");
        }

        try {
            final String pluginName;
            final File tempPluginDir;
            final URL url = asAbsoluteUrl();
            if (Files.exists(Paths.get(pluginFile))) {
                tempPluginDir = unzip(Paths.get(pluginFile).toFile());
                pluginName = getPluginName(tempPluginDir);
            } else if (url != null) {
                final File tempFile = get(url);
                tempPluginDir = unzip(tempFile);
                pluginName = getPluginName(tempPluginDir);
            } else {
                final Registry plugin = readRegistry();
                if (plugin == null) {
                    throw new BuildException("Unable to find plugin " + pluginFile);
                }
                final File tempFile = get(plugin.url);
                // TODO: verify checksum
                //  <local name="install.download.checksum"/>
                //  <checksum file="${dita.temp.dir}/${install.download.file}" algorithm="SHA-256"
                //    property="install.download.checksum"/>
                //
                //  <fail message="Downloaded plugin file checksum does not match expected value">
                //    <condition>
                //      <not>
                //        <equals arg1="${plugin.file.checksum}" arg2="${install.download.checksum}"/>
                //      </not>
                //    </condition>
                //  </fail>
                tempPluginDir = unzip(tempFile);
                pluginName = plugin.name;
            }
            final File pluginDir = getPluginDir(pluginName);
            if (pluginDir.exists()) {
                throw new BuildException(new IllegalStateException(String.format("Plug-in %s already exists: %s", pluginName, pluginDir)));
            }
            Files.move(tempPluginDir.toPath(), pluginDir.toPath());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        } finally {
            cleanUp();
        }
    }

    private String getPluginName(final File pluginDir) {
        final File config = new File(pluginDir, "plugin.xml");
        try {
            final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config);
            return doc.getDocumentElement().getAttribute("id");
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new BuildException("Failed to read plugin name: " + e.getMessage(), e);
        }
    }

    private File getPluginDir(final String id) {
        return Paths.get(getProject().getProperty("dita.dir"), "plugins", id).toFile();
    }

    private Registry readRegistry() {
        final String name;
        final SemVer version;
        if (pluginFile.contains("@")) {
            final String[] tokens = pluginFile.split("@");
            name = tokens[0];
            version = new SemVer(tokens[1]);
        } else {
            name = pluginFile;
            version = null;
        }
        for (final String registry : registries) {
            final URI registryUrl = URI.create(registry + name + ".json");
            log(String.format("Read registry %s", registry), Project.MSG_DEBUG);
            try (BufferedInputStream in = new BufferedInputStream(registryUrl.toURL().openStream())) {
                log("Parse registry", Project.MSG_DEBUG);
                final List<Registry> regs = Arrays.asList(mapper.readValue(in, Registry[].class));
                final Optional<Registry> reg = getRegistry(regs, version);
                if (reg.isPresent()) {
                    final Registry plugin = reg.get();
                    log(String.format("Plugin found at %s@%s", registryUrl, plugin.vers), Project.MSG_INFO);
                    return plugin;
                }
            } catch (MalformedURLException e) {
                log(String.format("Invalid registry URL %s: %s", registryUrl, e.getMessage()), e, Project.MSG_ERR);
            } catch (FileNotFoundException e) {
                log(String.format("Registry configuration %s not found", registryUrl), e, Project.MSG_DEBUG);
            } catch (IOException e) {
                log(String.format("Failed to read registry configuration %s: %s", registryUrl, e.getMessage()), e, Project.MSG_ERR);
            }
        }
        return null;
    }

    private File get(final URL url) {
        final File tempPluginFile = new File(tempDir, "plugin.zip");

        final Get get = new Get();
        get.setProject(getProject());
        get.setTaskName("get");
        get.setSrc(url);
        get.setDest(tempPluginFile);
        get.setIgnoreErrors(false);
        get.setVerbose(false);
        get.execute();

        return tempPluginFile;
    }

    private File unzip(final File input) {
        final File tempPluginDir = new File(tempDir, "plugin");

        final Expand unzip = new Expand();
        unzip.setProject(getProject());
        unzip.setTaskName("unzip");
        unzip.setSrc(input);
        unzip.setDest(tempPluginDir);
        unzip.execute();

        return findBaseDir(tempPluginDir);
    }

    private File findBaseDir(final File tempPluginDir) {
        final File config = new File(tempPluginDir, "plugin.xml");
        if (config.exists()) {
            return tempPluginDir;
        } else {
            for (final File dir : tempPluginDir.listFiles(File::isDirectory)) {
                final File res = findBaseDir(dir);
                if (res != null) {
                    return res;
                }
            }
            return null;
        }
    }

    private Optional<Registry> getRegistry(List<Registry> regs, final SemVer version) {
        if (version == null) {
            return regs.stream()
                    .filter(this::matchingPlatformVersion)
                    .max(Comparator.comparing(o -> o.vers));
        } else {
            return regs.stream()
                    .filter(this::matchingPlatformVersion)
                    .filter(reg -> reg.vers.equals(version))
                    .findFirst();
        }
    }

    @VisibleForTesting
    boolean matchingPlatformVersion(Registry reg) {
        final Optional<Dependency> platformDependency = reg.deps.stream()
                .filter(dep -> dep.name.equals("org.dita.base"))
                .findFirst();
        if (platformDependency.isPresent()) {
            final SemVer platform = new SemVer(Configuration.configuration.get("otversion"));
            final Dependency dep = platformDependency.get();
            return dep.req.contains(platform);
        } else {
            return true;
        }
    }

    public void setPluginFile(final String pluginFile) {
        this.pluginFile = pluginFile;
    }

}
