/*
 * (C) Copyright 2006-20012 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.platform.template.processors.docx;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.tree.DefaultElement;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.common.utils.ZipUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.platform.template.InputType;
import org.nuxeo.ecm.platform.template.TemplateInput;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.processors.AbstractTemplateProcessor;
import org.nuxeo.ecm.platform.template.processors.BidirectionalTemplateProcessor;

/**
 * WordXML implementation of the {@link BidirectionalTemplateProcessor}. Uses
 * Raw XML parsing : legacy code for now.
 * 
 * @author Tiry (tdelprat@nuxeo.com)
 * 
 */
public class WordXMLRawTemplateProcessor extends AbstractTemplateProcessor
        implements BidirectionalTemplateProcessor {

    public static SimpleDateFormat wordXMLDateFormat = new SimpleDateFormat(
            "yyyy-MM-dd'T'hh:mm:ss'Z'");

    public static final String TEMPLATE_TYPE = "wordXMLTemplate";

    @SuppressWarnings("rawtypes")
    public Blob renderTemplate(TemplateBasedDocument templateDocument,
            String templateName) throws Exception {

        File workingDir = getWorkingDir();

        Blob blob = templateDocument.getTemplateBlob(templateName);
        String fileName = blob.getFilename();
        List<TemplateInput> params = templateDocument.getParams(templateName);
        File sourceZipFile = File.createTempFile("WordXMLTemplate", ".zip");
        blob.transferTo(sourceZipFile);

        ZipUtils.unzip(sourceZipFile, workingDir);

        File xmlCustomFile = new File(workingDir.getAbsolutePath()
                + "/docProps/custom.xml");

        String xmlContent = FileUtils.readFile(xmlCustomFile);

        Document xmlDoc = DocumentHelper.parseText(xmlContent);

        List nodes = xmlDoc.getRootElement().elements();

        for (Object node : nodes) {
            DefaultElement elem = (DefaultElement) node;
            if ("property".equals(elem.getName())) {
                String name = elem.attributeValue("name");
                TemplateInput param = getParamByName(name, params);
                DefaultElement valueElem = (DefaultElement) elem.elements().get(
                        0);
                String strValue = "";
                if (param.isSourceValue()) {
                    Property property = templateDocument.getAdaptedDoc().getProperty(
                            param.getSource());
                    if (property != null) {
                        Serializable value = templateDocument.getAdaptedDoc().getPropertyValue(
                                param.getSource());
                        if (value != null) {
                            if (value instanceof Date) {
                                strValue = wordXMLDateFormat.format((Date) value);
                            } else {
                                strValue = value.toString();
                            }
                        }
                    }
                } else {
                    if (InputType.StringValue.equals(param.getType())) {
                        strValue = param.getStringValue();
                    } else if (InputType.BooleanValue.equals(param.getType())) {
                        strValue = param.getBooleanValue().toString();
                    } else if (InputType.DateValue.equals(param.getType())) {
                        strValue = wordXMLDateFormat.format(param.getDateValue());
                    }
                }
                valueElem.setText(strValue);
            }
        }

        String newXMLContent = xmlDoc.asXML();

        File newZipFile = File.createTempFile("newWordXMLTemplate", ".docx");
        xmlCustomFile.delete();
        File newXMLFile = new File(xmlCustomFile.getAbsolutePath());
        FileUtils.writeFile(newXMLFile, newXMLContent);

        File[] files = workingDir.listFiles();
        ZipUtils.zip(files, newZipFile);

        // clean up
        FileUtils.deleteTree(workingDir);
        sourceZipFile.delete();
        newZipFile.deleteOnExit();

        Blob newBlob = new FileBlob(newZipFile);
        newBlob.setFilename(fileName);

        return newBlob;
    }

    @SuppressWarnings("rawtypes")
    public List<TemplateInput> getInitialParametersDefinition(Blob blob)
            throws Exception {
        List<TemplateInput> params = new ArrayList<TemplateInput>();

        String xmlContent = readPropertyFile(blob.getStream());

        Document xmlDoc = DocumentHelper.parseText(xmlContent);

        List nodes = xmlDoc.getRootElement().elements();

        for (Object node : nodes) {
            DefaultElement elem = (DefaultElement) node;
            if ("property".equals(elem.getName())) {
                String name = elem.attributeValue("name");
                DefaultElement valueElem = (DefaultElement) elem.elements().get(
                        0);
                String wordType = valueElem.getName();
                InputType nxType = InputType.StringValue;
                if (wordType.contains("lpwstr")) {
                    nxType = InputType.StringValue;
                } else if (wordType.contains("filetime")) {
                    nxType = InputType.DateValue;
                } else if (wordType.contains("bool")) {
                    nxType = InputType.BooleanValue;
                }

                TemplateInput input = new TemplateInput(name);
                input.setType(nxType);
                params.add(input);
            }
        }
        return params;
    }

    protected TemplateInput getParamByName(String name,
            List<TemplateInput> params) {
        for (TemplateInput param : params) {
            if (param.getName().equals(name)) {
                return param;
            }
        }
        return null;
    }

    public String readPropertyFile(InputStream in) throws Exception {
        ZipInputStream zIn = new ZipInputStream(in);
        ZipEntry zipEntry = zIn.getNextEntry();
        String xmlContent = null;
        while (zipEntry != null) {
            if (zipEntry.getName().equals("docProps/custom.xml")) {
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = zIn.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, read));
                }
                xmlContent = sb.toString();
                break;
            }
            zipEntry = zIn.getNextEntry();
        }
        zIn.close();
        return xmlContent;
    }

    @SuppressWarnings("rawtypes")
    public DocumentModel updateDocumentFromBlob(
            TemplateBasedDocument templateDocument, String templateName)
            throws Exception {

        Blob blob = templateDocument.getTemplateBlob(templateName);

        String xmlContent = readPropertyFile(blob.getStream());

        if (xmlContent == null) {
            return templateDocument.getAdaptedDoc();
        }

        Document xmlDoc = DocumentHelper.parseText(xmlContent);

        List nodes = xmlDoc.getRootElement().elements();

        DocumentModel adaptedDoc = templateDocument.getAdaptedDoc();
        List<TemplateInput> params = templateDocument.getParams(templateName);

        for (Object node : nodes) {
            DefaultElement elem = (DefaultElement) node;
            if ("property".equals(elem.getName())) {
                String name = elem.attributeValue("name");
                TemplateInput param = getParamByName(name, params);
                DefaultElement valueElem = (DefaultElement) elem.elements().get(
                        0);
                String xmlValue = valueElem.getTextTrim();
                if (param.isSourceValue()) {
                    // XXX this needs to be rewritten

                    if (String.class.getSimpleName().equals(param.getType())) {
                        adaptedDoc.setPropertyValue(param.getSource(), xmlValue);
                    } else if (InputType.BooleanValue.equals(param.getType())) {
                        adaptedDoc.setPropertyValue(param.getSource(),
                                new Boolean(xmlValue));
                    } else if (Date.class.getSimpleName().equals(
                            param.getType())) {
                        adaptedDoc.setPropertyValue(param.getSource(),
                                wordXMLDateFormat.parse(xmlValue));
                    }
                } else {
                    if (InputType.StringValue.equals(param.getType())) {
                        param.setStringValue(xmlValue);
                    } else if (InputType.BooleanValue.equals(param.getType())) {
                        param.setBooleanValue(new Boolean(xmlValue));
                    } else if (InputType.DateValue.equals(param.getType())) {
                        param.setDateValue(wordXMLDateFormat.parse(xmlValue));
                    }
                }
            }
        }
        adaptedDoc = templateDocument.saveParams(templateName, params, false);
        return adaptedDoc;
    }

}
