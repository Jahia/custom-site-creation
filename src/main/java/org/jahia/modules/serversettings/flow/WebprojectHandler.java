/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect�?, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.modules.serversettings.flow;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import org.apache.commons.lang.StringUtils;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.exceptions.JahiaException;
import org.jahia.modules.sitesettings.users.management.UserProperties;
import org.jahia.osgi.BundleResource;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.importexport.ImportExportBaseService;
import org.jahia.services.render.RenderContext;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.templates.JahiaTemplateManagerService;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Url;
import org.jahia.utils.i18n.Messages;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.webflow.execution.RequestContext;

import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handle creation of Web projects in webflow.
 */
public class WebprojectHandler implements Serializable {

    private static final Comparator<ImportInfo> IMPORTS_COMPARATOR = new Comparator<ImportInfo>() {
        public int compare(ImportInfo o1, ImportInfo o2) {
            Integer rank1 = RANK.get(o1.getImportFileName());
            Integer rank2 = RANK.get(o2.getImportFileName());
            rank1 = rank1 != null ? rank1 : 100;
            rank2 = rank2 != null ? rank2 : 100;
            return rank1.compareTo(rank2);
        }
    };

    private static final Pattern LANGUAGE_RANK_PATTERN = Pattern.compile("(?:language.)(\\w+)(?:.rank)");

    static Logger logger = LoggerFactory.getLogger(WebprojectHandler.class);
    private static final HashSet<String> NON_SITE_IMPORTS = new HashSet<>(Arrays.asList("serverPermissions.xml",
            "users.xml", "users.zip", JahiaSitesService.SYSTEM_SITE_KEY + ".zip", "references.zip", "roles.zip", "mounts.zip"));
    private static final Map<String, Integer> RANK;

    private static final long serialVersionUID = -6643519526225787438L;

    static {
        RANK = new HashMap<>(8);
        RANK.put("mounts.zip", 4);
        RANK.put("roles.xml", 5);
        RANK.put("roles.zip", 5);
        RANK.put("users.zip", 10);
        RANK.put("users.xml", 10);
        RANK.put("serverPermissions.xml", 20);
        RANK.put("shared.zip", 30);
        RANK.put(JahiaSitesService.SYSTEM_SITE_KEY + ".zip", 40);
    }

    @Autowired
    private transient JahiaGroupManagerService groupManagerService;

    @Autowired
    private transient ImportExportBaseService importExportBaseService;

    private transient MultipartFile importFile;
    private String importPath;
    private Properties importProperties;

    private Map<String, ImportInfo> importsInfos = Collections.emptyMap();
    private boolean deleteFilesAtEnd = true;

    private Map<String, String> prepackagedSites = new HashMap<String, String>();

    private transient List<JahiaSite> sites;

    private List<String> sitesKey;

    @Autowired
    private transient JahiaSitesService sitesService;

    @Autowired
    private transient JahiaTemplateManagerService templateManagerService;

    @Autowired
    private transient JahiaUserManagerService userManagerService;


    @Autowired
    private transient JCRTemplate template;


    private boolean validityCheckOnImport = true;

    public WebprojectHandler() {
        prepackagedSites = new HashMap<String, String>();
        File[] files = new File(SettingsBean.getInstance().getJahiaVarDiskPath() + "/prepackagedSites").listFiles();
        if (files != null) {
            for (File file : files) {
                prepackagedSites.put(file.getAbsolutePath(), Messages.get("resources.JahiaServerSettings", "serverSettings.manageWebProjects.importprepackaged." + file.getName(), LocaleContextHolder.getLocale(), file.getName()));
            }
        }
        for (final JahiaTemplatesPackage aPackage : ServicesRegistry.getInstance().getJahiaTemplateManagerService().getAvailableTemplatePackages()) {
            final Bundle bundle = aPackage.getBundle();
            if (bundle != null) {
                final Enumeration<URL> resourceEnum = bundle.findEntries("META-INF/prepackagedSites/", "*", false);
                if (resourceEnum == null) continue;
                while (resourceEnum.hasMoreElements()) {
                    BundleResource bundleResource = new BundleResource(resourceEnum.nextElement(), bundle);
                    try {
                        String title = bundleResource.getFilename();
                        try {
                            String titleKey = "serverSettings.manageWebProjects.importprepackaged." + bundleResource.getFilename();
                            title = Messages.get(aPackage, titleKey, LocaleContextHolder.getLocale(), bundleResource.getFilename());
                        } catch (MissingResourceException e) {
                            logger.warn("unable to get resource key " + "serverSettings.manageWebProjects.importprepackaged." + bundleResource.getFilename() + " in package" + bundle.getSymbolicName());
                        }
                        prepackagedSites.put(bundleResource.getURI().toString() + "#" + bundle.getSymbolicName(), title);
                    } catch (IOException e) {
                        logger.warn("unable to read prepackaged site " + bundleResource.getFilename());
                    }
                }
            }
        }
    }

    public List<JCRSiteNode> getAllSites() {
        try {
            Function<JCRSiteNode, String> getTitle = new Function<JCRSiteNode, String>() {
                public String apply(@Nullable JCRSiteNode input) {
                    return input != null ? input.getTitle() : "";
                }
            };
            Predicate<JCRSiteNode> notSystemSite = new Predicate<JCRSiteNode>() {
                @Override
                public boolean apply(JCRSiteNode jcrSiteNode) {
                    return !jcrSiteNode.getName().equals("systemsite");
                }
            };
            return Ordering.natural().onResultOf(getTitle).sortedCopy(Iterables.filter(sitesService.getSitesNodeList(), notSystemSite));
        } catch (RepositoryException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }


    public void createSite(final SiteBean bean) {

        try {
            template.doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    try {
                        JahiaSite site = sitesService
                                .addSite(JCRSessionFactory.getInstance().getCurrentUser(), bean.getTitle(),
                                        bean.getServerName(), bean.getSiteKey(), bean.getDescription(),
                                        LanguageCodeConverters.getLocaleFromCode(bean.getLanguage()),
                                        bean.getTemplateSet(), bean.getModules().toArray(
                                                new String[bean.getModules().size()]), null, null, null, null, null, null, null,
                                        null, session);

                        JCRSiteNode siteNode = (JCRSiteNode) site;
                        siteNode.addMixin("jmix:siteOptions");
                        siteNode.setProperty("siteType",bean.getSiteType());
                        siteNode.setProperty("siteTheme",bean.getSiteTheme());
                        if ("portal".equals(bean.getSiteType())){
                            siteNode.addMixin("jmix:siteTypePortal");
                        }
                        if (!"other".equals(bean.getSiteType())){
                            siteNode.getHome().setProperty("j:templateName",bean.getHomePageTemplate());
                        }

                        // Sets a custom workflow for the site

                        /*WorkflowService workflowService = WorkflowService.getInstance();
                        for (Locale locale : siteNode.getLanguagesAsLocales()) {
                            WorkflowDefinition workflowDefinition = workflowService.getWorkflowDefinition("jBPM","no-validation-workflow",locale);
                            if(workflowDefinition != null){
                                workflowService.addWorkflowRule(siteNode,workflowDefinition);
                            }else{
                                logger.error(" Workflow 'no-validation-workflow' is missing");
                            }
                        }*/
                        session.save();

                        session.save();
                        // set as default site
                        if (bean.isDefaultSite()) {
                            sitesService.setDefaultSite(site, session);
                            session.save();
                        }

                        if (bean.isCreateAdmin()) {
                            UserProperties admin = bean.getAdminProperties();
                            JCRUserNode adminSiteUser = userManagerService.createUser(admin.getUsername(), admin.getPassword(),
                                    admin.getUserProperties(), session);
                            groupManagerService.getAdministratorGroup(site.getSiteKey(), session).addMember(adminSiteUser);
                            session.save();
                        }

                    } catch (JahiaException | IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }
            });
        } catch (RepositoryException e) {
            logger.error(e.getMessage(), e);
        }

    }

    public SiteBean getNewSite() {
        return new SiteBean();
    }


    public void setGroupManagerService(JahiaGroupManagerService groupManagerService) {
        this.groupManagerService = groupManagerService;
    }

    public void setSitesService(JahiaSitesService sitesService) {
        this.sitesService = sitesService;
    }

    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    private void validateSite(MessageContext messageContext, ImportInfo infos) {
        try {
            infos.setSiteTitleInvalid(StringUtils.isEmpty(infos.getSiteTitle()));

            String siteKey = infos.getSiteKey();
            if (infos.isSite()) {
                boolean valid = sitesService.isSiteKeyValid(siteKey);
                if (!valid) {
                    messageContext.addMessage(new MessageBuilder()
                            .error()
                            .source("siteKey")
                            .code("serverSettings.manageWebProjects.invalidSiteKey").build());
                }
                if (valid && sitesService.getSiteByKey(siteKey) != null) {
                    messageContext.addMessage(new MessageBuilder()
                            .error()
                            .source("siteKey")
                            .code("serverSettings.manageWebProjects.siteKeyExists").build());
                }

                String serverName = infos.getSiteServername();
                if (infos.isLegacyImport() && (StringUtils.startsWithIgnoreCase(serverName, "http://") || StringUtils.startsWithIgnoreCase(serverName, "https://"))) {
                    serverName = StringUtils.substringAfter(serverName, "://");
                    infos.setSiteServername(serverName);
                }
                valid = sitesService.isServerNameValid(serverName);
                if (!valid) {
                    messageContext.addMessage(new MessageBuilder()
                            .error()
                            .source("siteKey")
                            .code("serverSettings.manageWebProjects.invalidServerName").build());
                }

                if (valid && !Url.isLocalhost(serverName) && sitesService.getSite(serverName) != null) {
                    messageContext.addMessage(new MessageBuilder()
                            .error()
                            .source("siteKey")
                            .code("serverSettings.manageWebProjects.serverNameExists").build());
                }
            }
        } catch (JahiaException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Returns the ID of the default template set.
     *
     * @return the ID of the default template set
     */
    public String getDefaultTemplateSetId() {
        String id = null;
        String defTemplateSet = StringUtils.defaultIfBlank(
                SettingsBean.getInstance().lookupString("default_templates_set"), StringUtils.EMPTY).trim();
        if (defTemplateSet.length() > 0) {
            JahiaTemplatesPackage pkg = templateManagerService.getTemplatePackage(defTemplateSet);
            if (pkg == null) {
                pkg = templateManagerService.getTemplatePackageById(defTemplateSet);
            }
            if (pkg == null) {
                logger.warn("Unable to find default template set \"{}\","
                        + " specified via default_templates_set (jahia.properties)");
            } else {
                id = pkg.getId();
            }
        }
        return id;
    }

    /**
     * Returns the total number of sites in Jahia.
     *
     * @return the total number of sites in Jahia
     * @throws JahiaException in case of an error
     */
    public int getNumberOfSites() throws JahiaException {
        return sitesService.getNbSites() - 1;
    }


    public String getAdminURL(RequestContext requestContext) {
        RenderContext renderContext = getRenderContext(requestContext);
        Locale locale = LocaleContextHolder.getLocale();
        String server = Url.getServer(renderContext.getRequest());
        String context = renderContext.getURLGenerator().getContext();
        return server + context + "/cms/adminframe/default/" + locale + "/settings.webProjectSettings.html";
    }

    private RenderContext getRenderContext(RequestContext requestContext) {
        return (RenderContext) requestContext.getExternalContext().getRequestMap().get("renderContext");
    }

}
