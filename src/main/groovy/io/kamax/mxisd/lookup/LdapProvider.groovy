/*
 * mxisd - Matrix Identity Server Daemon
 * Copyright (C) 2017 Maxime Dor
 *
 * https://max.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.mxisd.lookup

import io.kamax.mxisd.api.ThreePidType
import io.kamax.mxisd.config.LdapConfig
import org.apache.directory.api.ldap.model.cursor.EntryCursor
import org.apache.directory.api.ldap.model.entry.Attribute
import org.apache.directory.api.ldap.model.message.SearchScope
import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.LdapNetworkConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class LdapProvider implements ThreePidProvider {

    private Logger log = LoggerFactory.getLogger(LdapProvider.class)

    @Autowired
    private LdapConfig ldapCfg

    @Override
    int getPriority() {
        return 20
    }

    @Override
    Optional<?> find(ThreePidType type, String threePid) {
        log.info("Performing LDAP lookup ${threePid} of type ${type}")

        LdapConnection conn = new LdapNetworkConnection(ldapCfg.getHost(), ldapCfg.getPort())
        try {
            conn.bind(ldapCfg.getBindDn(), ldapCfg.getBindPassword())

            String searchQuery = ldapCfg.getQuery().replaceAll("%3pid", threePid)
            EntryCursor cursor = conn.search(ldapCfg.getBaseDn(), searchQuery, SearchScope.SUBTREE, ldapCfg.getAttribute())
            try {
                if (cursor.next()) {
                    Attribute attribute = cursor.get().get(ldapCfg.getAttribute())
                    if (attribute != null) {
                        return Optional.of([
                                address   : threePid,
                                medium    : type,
                                mxid      : attribute.get().toString(),
                                not_before: 0,
                                not_after : 9223372036854775807,
                                ts        : 0
                        ])
                    }
                }
            } finally {
                cursor.close()
            }
        } finally {
            conn.close()
        }

        return Optional.empty()
    }

}