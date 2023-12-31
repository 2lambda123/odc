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
package com.oceanbase.odc.service.feature;

public class DefaultFeatures implements Features {
    @Override
    public boolean supportsShowTrace() {
        return true;
    }

    @Override
    public boolean supportsViewObject() {
        return true;
    }

    @Override
    public boolean supportsOBTenant() {
        return true;
    }

    @Override
    public boolean supportsShowTenant() {
        return false;
    }

    @Override
    public boolean supportsProcedure() {
        return true;
    }

    @Override
    public boolean supportsSchemaPrefixInSql() {
        return true;
    }

    @Override
    public boolean supportsExplain() {
        return true;
    }

    @Override
    public boolean supportsAutoIncrement() {
        return true;
    }
}
