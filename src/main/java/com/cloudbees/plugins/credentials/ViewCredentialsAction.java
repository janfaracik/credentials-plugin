/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.model.RootAction;
import hudson.model.TopLevelItem;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.jenkins.ui.icon.IconSpec;

/**
 * An {@link Action} that lets you view the available credentials for any {@link ModelObject}.
 */
public class ViewCredentialsAction implements Action, IconSpec, AccessControlled {

    /**
     * Expose {@link CredentialsProvider#VIEW} for Jelly.
     */
    public static final Permission VIEW = CredentialsProvider.VIEW;

    /**
     * The context in which this {@link ViewCredentialsAction} was created.
     */
    private final ModelObject context;

    /**
     * Constructor.
     *
     * @param context the context.
     */
    public ViewCredentialsAction(ModelObject context) {
        this.context = context;
    }

    /**
     * Gets the context.
     *
     * @return the context.
     */
    public ModelObject getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return isVisible()
                ? "/plugin/credentials/images/48x48/credentials.png"
                : null;
    }

    /**
     * Exposes the {@link CredentialsStore} instances available to the {@link #getContext()}.
     *
     * @return the {@link CredentialsStore} instances available to the {@link #getContext()}.
     */
    @NonNull
    public List<CredentialsStore> getStores() {
        List<CredentialsStore> result = new ArrayList<CredentialsStore>();
        for (CredentialsStore s : CredentialsProvider.lookupStores(getContext())) {
            if (s.hasPermission(CredentialsProvider.VIEW)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.CredentialsStoreAction_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "credentials";
    }

    /**
     * Tests if the {@link ViewCredentialsAction} should be visible.
     *
     * @return {@code true} if the action should be visible.
     */
    public boolean isVisible() {
        if (context instanceof AccessControlled
                && !((AccessControlled) context).hasPermission(CredentialsProvider.VIEW)) {
            // must have permission
            return false;
        }
        for (CredentialsProvider p : CredentialsProvider.enabled(context)) {
            if (p.hasCredentialsDescriptors()) {
                // at least one provider must have the potential for at least one type
                return true;
            }
        }
        return false;
    }

    /**
     * Expose a Jenkins {@link Api}.
     *
     * @return the {@link Api}.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the credential entries.
     *
     * @return the credential entries.
     */
    public List<TableEntry> getTableEntries() {
        List<TableEntry> result = new ArrayList<TableEntry>();
        Item item = context instanceof Item ? (Item) context : null;
        ItemGroup group = context instanceof ItemGroup ? (ItemGroup) context
                : context instanceof User ? Jenkins.getActiveInstance() : null;
        for (CredentialsStore p : CredentialsProvider.lookupStores(context)) {
            if (p.hasPermission(CredentialsProvider.VIEW)) {
                for (Domain domain : p.getDomains()) {
                    for (Credentials c : p.getCredentials(domain)) {
                        result.add(new TableEntry(p.getProvider(), p, domain, c));
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return isVisible()
                ? "icon-credentials-credentials"
                : null;
    }

    /**
     * Returns the full name of the {@link #getContext()}.
     *
     * @return the full name of the {@link #getContext()}.
     */
    public final String getFullName() {
        String n;
        if (context instanceof Item) {
            n = ((Item) context).getFullName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullName();
        } else if (context instanceof User) {
            n = "user:" + ((User) context).getId();
        } else {
            n = "";
        }
        if (n.length() == 0) {
            return getUrlName();
        } else {
            return n + '/' + getUrlName();
        }
    }

    /**
     * Returns the full display name of the {@link #getContext()}.
     *
     * @return the full display name of the {@link #getContext()}.
     */
    public final String getFullDisplayName() {
        String n;
        if (context instanceof Item) {
            n = ((Item) context).getFullDisplayName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullDisplayName();
        } else if (context instanceof User) {
            n = Messages.CredentialsStoreAction_UserDisplayName(((User) context).getDisplayName());
        } else {
            // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
            n = Jenkins.getActiveInstance().getFullDisplayName();
        }
        if (n.length() == 0) {
            return getDisplayName();
        } else {
            return n + " \u00BB " + getDisplayName();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public ACL getACL() {
        final AccessControlled accessControlled =
                context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getActiveInstance();
        return new ACL() {
            @Override
            public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                if (accessControlled.getACL().hasPermission(a, permission)) {
                    for (CredentialsStore s : getStores()) {
                        if (s.hasPermission(a, permission)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {
        getACL().checkPermission(permission);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(@Nonnull Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * Add the {@link ViewCredentialsAction} to all {@link TopLevelItem} instances.
     */
    @Extension(ordinal = -1000)
    public static class TransientTopLevelItemActionFactoryImpl extends TransientActionFactory<TopLevelItem> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<TopLevelItem> type() {
            return TopLevelItem.class;
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull TopLevelItem target) {
            return Collections.singleton(new ViewCredentialsAction(target));
        }
    }

    /**
     * Add the {@link ViewCredentialsAction} to all {@link User} instances.
     */
    @Extension(ordinal = -1000)
    public static class TransientUserActionFactoryImpl extends TransientUserActionFactory {
        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends Action> createFor(User target) {
            return Collections.singleton(new ViewCredentialsAction(target));
        }
    }

    /**
     * Add the {@link ViewCredentialsAction} to the {@link Jenkins} root.
     */
    @Extension(ordinal = -1000)
    public static class RootActionImpl extends ViewCredentialsAction implements RootAction {

        /**
         * Our constructor.
         */
        public RootActionImpl() {
            super(Jenkins.getActiveInstance());
        }
    }

    /**
     * Value class to simplify creating the table.
     */
    public static class TableEntry implements IconSpec {
        /**
         * The backing {@link Credentials}.
         */
        private final Credentials credentials;
        /**
         * The backing {@link CredentialsProvider}.
         */
        private final CredentialsProvider provider;
        /**
         * The backing {@link CredentialsStore}.
         */
        private final CredentialsStore store;
        /**
         * The backing {@link Domain}.
         */
        private final Domain domain;

        /**
         * Constructor.
         *
         * @param provider    the backing {@link CredentialsProvider}.
         * @param store       the backing {@link CredentialsStore}.
         * @param domain      the backing {@link Domain}.
         * @param credentials the backing {@link Credentials}.
         */
        public TableEntry(CredentialsProvider provider, CredentialsStore store,
                          Domain domain, Credentials credentials) {
            this.provider = provider;
            this.store = store;
            this.domain = domain;
            this.credentials = credentials;
        }

        /**
         * Returns the {@link IdCredentials#getId()} of the {@link #credentials}.
         *
         * @return the {@link IdCredentials#getId()} of the {@link #credentials}.
         */
        public String getId() {
            return credentials instanceof IdCredentials ? ((IdCredentials) credentials).getId() : null;
        }

        /**
         * Returns the {@link Credentials#getScope()} of the {@link #credentials}.
         *
         * @return the {@link Credentials#getScope()} of the {@link #credentials}.
         */
        public CredentialsScope getScope() {
            return credentials.getScope();
        }

        /**
         * Returns the {@link CredentialsNameProvider#name(Credentials)} of the {@link #credentials}.
         *
         * @return the {@link CredentialsNameProvider#name(Credentials)} of the {@link #credentials}.
         */
        public String getName() {
            return CredentialsNameProvider.name(credentials);
        }

        /**
         * Returns the {@link StandardCredentials#getDescription()} of the {@link #credentials}.
         *
         * @return the {@link StandardCredentials#getDescription()} of the {@link #credentials}.
         * @throws IOException if there was an issue with formatting this using the markup formatter.
         */
        public String getDescription() throws IOException {
            return credentials instanceof StandardCredentials ? Jenkins.getActiveInstance().getMarkupFormatter()
                    .translate(((StandardCredentials) credentials).getDescription()) : null;
        }

        /**
         * Returns the {@link CredentialsDescriptor#getDisplayName()}.
         *
         * @return the {@link CredentialsDescriptor#getDisplayName()}.
         */
        public String getKind() {
            return credentials.getDescriptor().getDisplayName();
        }

        /**
         * Exposes the {@link CredentialsProvider}.
         *
         * @return the {@link CredentialsProvider}.
         */
        public CredentialsProvider getProvider() {
            return provider;
        }

        /**
         * Exposes the {@link Domain}.
         *
         * @return the {@link Domain}.
         */
        public Domain getDomain() {
            return domain;
        }

        /**
         * Exposes the {@link CredentialsStore}.
         *
         * @return the {@link CredentialsStore}.
         */
        public CredentialsStore getStore() {
            return store;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return credentials.getDescriptor().getIconClassName();
        }
    }

}