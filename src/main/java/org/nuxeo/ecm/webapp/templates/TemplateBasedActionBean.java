package org.nuxeo.ecm.webapp.templates;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.util.ArrayList;
import java.util.List;

import javax.faces.context.FacesContext;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.template.TemplateInput;
import org.nuxeo.ecm.platform.template.adapters.doc.TemplateBasedDocument;
import org.nuxeo.ecm.platform.template.adapters.source.TemplateSourceDocument;
import org.nuxeo.ecm.platform.template.service.TemplateProcessorService;
import org.nuxeo.ecm.platform.ui.web.api.WebActions;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.webapp.contentbrowser.DocumentActions;
import org.nuxeo.ecm.webapp.helpers.EventManager;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;

@Name("templateBasedActions")
@Scope(CONVERSATION)
public class TemplateBasedActionBean extends BaseTemplateAction {

    private static final long serialVersionUID = 1L;

    @In(create = true)
    protected transient DocumentActions documentActions;

    @In(create = true)
    protected transient WebActions webActions;

    protected List<TemplateInput> templateInputs;

    protected List<TemplateInput> templateEditableInputs;

    protected TemplateInput newInput;

    protected String templateIdToAssociate;

    protected String editableTemplateName;

    public String createTemplate() throws Exception {
        DocumentModel changeableDocument = navigationContext.getChangeableDocument();
        TemplateSourceDocument sourceTemplate = changeableDocument.getAdapter(TemplateSourceDocument.class);
        if (sourceTemplate != null && sourceTemplate.getTemplateBlob() != null) {
            try {
                sourceTemplate.initTemplate(false);
                if (sourceTemplate.hasEditableParams()) {
                    templateInputs = sourceTemplate.getParams();
                    return "editTemplateRelatedData";
                }
            } catch (Exception e) {
                log.error("Error during parameter automatic initialization", e);
            }
        }
        return documentActions.saveDocument(changeableDocument);
    }

    public List<TemplateInput> getTemplateInputs() {
        return templateInputs;
    }

    public void setTemplateInputs(List<TemplateInput> templateInputs) {
        this.templateInputs = templateInputs;
    }

    public String saveDocument() throws Exception {
        DocumentModel changeableDocument = navigationContext.getChangeableDocument();

        for (TemplateInput ti : templateInputs) {
            log.info(ti.toString());
        }
        TemplateSourceDocument source = changeableDocument.getAdapter(TemplateSourceDocument.class);
        if (source != null) {
            source.saveParams(templateInputs, false);
        }

        return documentActions.saveDocument(changeableDocument);
    }

    @Observer(value = { EventNames.DOCUMENT_SELECTION_CHANGED,
            EventNames.NEW_DOCUMENT_CREATED, EventNames.DOCUMENT_CHANGED }, create = false)
    @BypassInterceptors
    public void reset() {
        templateInputs = null;
        templateEditableInputs = null;
        editableTemplateName = null;
        templateIdToAssociate = null;
    }

    public List<TemplateInput> getTemplateEditableInputs() throws Exception {
        if (editableTemplateName == null) {
            return new ArrayList<TemplateInput>();
        }
        if (templateEditableInputs == null) {
            DocumentModel currentDocument = navigationContext.getCurrentDocument();

            TemplateBasedDocument templateBasedDoc = currentDocument.getAdapter(TemplateBasedDocument.class);
            templateEditableInputs = templateBasedDoc.getParams(editableTemplateName);
        }
        return templateEditableInputs;
    }

    public void setTemplateEditableInputs(
            List<TemplateInput> templateEditableInputs) {
        this.templateEditableInputs = templateEditableInputs;
    }

    public String saveTemplateInputs() throws Exception {

        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {
            currentDocument = template.saveParams(templateEditableInputs, true);
        }
        return navigationContext.navigateToDocument(currentDocument);
    }

    public TemplateInput getNewInput() {
        if (newInput == null) {
            newInput = new TemplateInput("newField");
        }
        return newInput;
    }

    public void setNewInput(TemplateInput newInput) {
        this.newInput = newInput;
    }

    public String addTemplateInput() throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();

        TemplateSourceDocument template = currentDocument.getAdapter(TemplateSourceDocument.class);
        if (template != null) {
            template.addInput(newInput);
            newInput = null;
            templateEditableInputs = null;
        } else {
            return null;
        }

        return navigationContext.navigateToDocument(currentDocument);
    }

    public String render(String templateName) throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        TemplateBasedDocument doc = currentDocument.getAdapter(TemplateBasedDocument.class);
        if (doc == null) {
            return null;
        }

        // XXX handle rendering error
        Blob rendition = doc.renderWithTemplate(templateName);
        String filename = rendition.getFilename();
        FacesContext context = FacesContext.getCurrentInstance();
        return ComponentUtils.download(context, rendition, filename);
    }

    public String renderAndStore(String templateName) throws Exception {

        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        TemplateBasedDocument doc = currentDocument.getAdapter(TemplateBasedDocument.class);
        if (doc == null) {
            return null;
        }
        doc.renderAndStoreAsAttachment(templateName, true);
        documentManager.save();
        return navigationContext.navigateToDocument(doc.getAdaptedDoc());
    }

    public boolean canResetParameters() throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        TemplateBasedDocument templateBased = currentDocument.getAdapter(TemplateBasedDocument.class);
        if (templateBased != null) {
            return true;
        }
        return false;
    }

    public void resetParameters(String templateName) throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        TemplateBasedDocument templateBased = currentDocument.getAdapter(TemplateBasedDocument.class);
        if (templateBased != null) {
            templateBased.initializeFromTemplate(templateName, true);
            templateEditableInputs = null;
        }
    }

    public String detachTemplate(String templateName) throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        currentDocument = tps.detachTemplateBasedDocument(currentDocument,
                templateName, true);
        webActions.resetTabList();
        return navigationContext.navigateToDocument(currentDocument);
    }

    public String getTemplateIdToAssociate() {
        return templateIdToAssociate;
    }

    public void setTemplateIdToAssociate(String templateIdToAssociate) {
        this.templateIdToAssociate = templateIdToAssociate;
    }

    public String associateDocumentToTemplate() throws ClientException {
        if (templateIdToAssociate == null) {
            return null;
        }
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        DocumentModel sourceTemplate = documentManager.getDocument(new IdRef(
                templateIdToAssociate));
        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        currentDocument = tps.makeTemplateBasedDocument(currentDocument,
                sourceTemplate, true);
        navigationContext.invalidateCurrentDocument();
        EventManager.raiseEventsOnDocumentChange(currentDocument);
        templateIdToAssociate = null;
        return navigationContext.navigateToDocument(currentDocument,
                "after-edit");
    }

    public boolean canRenderAndStore() {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        // check that templating is supported
        TemplateBasedDocument template = currentDocument.getAdapter(TemplateBasedDocument.class);
        if (template == null) {
            return false;
        }
        // check that we can store the result : XXX do better
        BlobHolder bh = currentDocument.getAdapter(BlobHolder.class);
        if (bh == null) {
            return false;
        }
        return true;
    }

    public String getEditableTemplateName() {
        return editableTemplateName;
    }

    public void setEditableTemplateName(String editableTemplateName) {
        if (editableTemplateName == null
                || !editableTemplateName.equals(this.editableTemplateName)) {
            this.editableTemplateName = editableTemplateName;
            templateEditableInputs = null;
        }
    }

    public List<TemplateSourceDocument> getBindableTemplatesForDocument()
            throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        String targetType = currentDocument.getType();
        TemplateProcessorService tps = Framework.getLocalService(TemplateProcessorService.class);
        List<DocumentModel> templates = tps.getAvailableTemplateDocs(
                documentManager, targetType);

        List<TemplateSourceDocument> result = new ArrayList<TemplateSourceDocument>();
        TemplateBasedDocument currentTBD = currentDocument.getAdapter(TemplateBasedDocument.class);
        List<String> alreadyBoundTemplateNames = new ArrayList<String>();
        if (currentTBD != null) {
            alreadyBoundTemplateNames = currentTBD.getTemplateNames();
        }
        for (DocumentModel doc : templates) {
            TemplateSourceDocument source = doc.getAdapter(TemplateSourceDocument.class);
            if (!alreadyBoundTemplateNames.contains(source.getName())) {
                result.add(source);
            }
        }
        return result;

    }

    public boolean canBindNewTemplate() throws Exception {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (!currentDocument.getCoreSession().hasPermission(
                currentDocument.getRef(), SecurityConstants.WRITE)) {
            return false;
        }
        if (getBindableTemplatesForDocument().size() == 0) {
            return false;
        }
        return true;
    }

    @Factory(value = "currentTemplateBasedDocument", scope = ScopeType.EVENT)
    public TemplateBasedDocument getCurrentDocumentAsTemplateBasedDocument() {
        return navigationContext.getCurrentDocument().getAdapter(
                TemplateBasedDocument.class);
    }
}
