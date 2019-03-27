/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2019 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */

package org.dita.dost.project;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.dita.dost.project.Project.Context;
import org.dita.dost.project.Project.Deliverable.Inputs;
import org.dita.dost.project.Project.Deliverable.Inputs.Input;
import org.dita.dost.project.Project.Deliverable.Profile;
import org.dita.dost.project.Project.Deliverable.Profile.DitaVal;
import org.dita.dost.project.Project.Publication;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ProjectTest {

    private final ObjectReader jsonReader = new ObjectMapper().readerFor(Project.class);
    private final ObjectReader xmlReader = new XmlMapper().readerFor(Project.class);
    private final ObjectWriter jsonWriter = new ObjectMapper().writerFor(Project.class).with(SerializationFeature.INDENT_OUTPUT);
    private final ObjectWriter xmlWriter = new XmlMapper().writerFor(Project.class).with(SerializationFeature.INDENT_OUTPUT);


    @Test
    @Ignore
    public void deserializeXmlSimple() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/simple.xml")) {
            final Project project = xmlReader.readValue(in);
            assertNotNull(project.deliverables);
            assertNull(project.includes);
        }
    }

    @Test
    public void deserializeJsonSimple() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/simple.json")) {
            final Project project = jsonReader.readValue(in);
            assertNotNull(project.deliverables);
            assertNull(project.includes);
        }
    }

    @Test
    @Ignore
    public void deserializeXmlCommon() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/common.xml")) {
            final Project project = xmlReader.readValue(in);
            assertNull(project.deliverables);
            assertNull(project.includes);
        }
    }

    @Test
    public void deserializeJsonCommon() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/common.json")) {
            final Project project = jsonReader.readValue(in);
            assertNull(project.deliverables);
            assertNull(project.includes);
        }
    }

    @Test
    public void deserializeJsonProduct() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/product.json")) {
            final Project project = jsonReader.readValue(input);
            new XmlMapper().writerFor(Project.class).writeValueAsString(project);
        }
    }

    @Test
    @Ignore
    public void deserializeXmlProduct() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("org/dita/dost/project/product.xml")) {
            final Project project = xmlReader.readValue(input);
            assertEquals(1, project.deliverables.size());
            assertEquals(1, project.publications.size());
            assertEquals("common-sitePub2", project.deliverables.get(0).publication.id);
        }
    }

    @Test
    public void serializeXmlSimple() throws IOException {
        final Project project = getProject();
        xmlWriter.writeValueAsString(project);
    }

    @Test
    public void serializeJsonSimple() throws IOException {
        final Project project = getProject();
        jsonWriter.writeValueAsString(project);
    }

    @Test
    public void serializeJsonRoot() throws IOException {
        final Project project = new Project(null, Arrays.asList(new Project.ProjectRef(URI.create("simple.json"))), null, null);
        jsonWriter.writeValueAsString(project);
    }

    private Project getProject() {
        return new Project(Arrays.asList(new Project.Deliverable(
                "name",
                new Context("Site", "site", null,
                        new Inputs(//"inputs-name",
//                        "inputs-ref",
                                Arrays.asList(new Input(URI.create("site.ditamap")))),
                        new Profile(//"profile-name",
//                        "profile-ref",
                                Arrays.asList(new DitaVal(URI.create("site.ditaval"))))
                ),
                URI.create("./site"),
                new Publication("Site", "site", null, "html5", Arrays.asList(
                        new Publication.Param("args.gen.task.lbl", "YES", null),
                        new Publication.Param("args.rellinks", "noparent", null)
                ))
        )), null, null, null);
    }
}
