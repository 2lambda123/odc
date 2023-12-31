/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.connection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.oceanbase.odc.service.connection.model.ConnectionStringParseResult;
import com.oceanbase.odc.service.connection.model.GenerateConnectionStringReq;
import com.oceanbase.odc.service.connection.model.ParseConnectionStringReq;
import com.oceanbase.odc.service.encryption.SensitivePropertyHandler;

public class ConnectionHelperTest {

    @InjectMocks
    private ConnectionHelper helper;

    @Spy
    private SensitivePropertyHandler sensitivePropertyHandler = new EmptySensitivePropertyHandler();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void generateConnectionStr() {
        GenerateConnectionStringReq req = buildGenerateConnectionStringReq();

        String connectionStr = helper.generateConnectionStr(req);

        Assert.assertEquals("obclient -h127.0.0.1 -P46774 -uroot@sys#C1 -Doceanbase -p'pwd'", connectionStr);
    }

    @Test
    public void generateConnectionStr_ODP_NotClusterTenant() {
        GenerateConnectionStringReq req = buildGenerateConnectionStringReq();
        req.setClusterName(null);
        req.setTenantName(null);

        String connectionStr = helper.generateConnectionStr(req);

        Assert.assertEquals("obclient -h127.0.0.1 -P46774 -uroot -Doceanbase -p'pwd'", connectionStr);
    }

    @Test
    public void parseConnectionStr() {
        String connStr = "obclient -h127.0.0.1  -P2883   -uroot@tenant1#C1 -Doceanbase -ppwd";
        ParseConnectionStringReq req = new ParseConnectionStringReq();
        req.setConnStr(connStr);

        ConnectionStringParseResult session = helper.parseConnectionStr(req);

        ConnectionStringParseResult expected = new ConnectionStringParseResult();
        expected.setHost("127.0.0.1");
        expected.setPort(2883);
        expected.setClusterName("C1");
        expected.setTenantName("tenant1");
        expected.setUsername("root");
        expected.setDefaultSchema("oceanbase");
        expected.setPassword("pwd");

        Assert.assertEquals(expected, session);
    }

    private GenerateConnectionStringReq buildGenerateConnectionStringReq() {
        GenerateConnectionStringReq req = new GenerateConnectionStringReq();
        req.setClusterName("C1");
        req.setTenantName("sys");
        req.setHost("127.0.0.1");
        req.setPort(46774);
        req.setUsername("root");
        req.setDefaultSchema("oceanbase");
        req.setPassword("pwd");
        return req;
    }

    private static class EmptySensitivePropertyHandler implements SensitivePropertyHandler {
        @Override
        public String encryptionSecret() {
            return null;
        }

        @Override
        public String encrypt(String plainText) {
            return plainText;
        }

        @Override
        public String decrypt(String encryptedText) {
            return encryptedText;
        }
    }

}
