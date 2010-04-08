/*
 * Copyright (c) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.http.android;

import android.accounts.Account;
import android.accounts.AccountManager;

import com.google.api.client.http.LowLevelHttpTransport;
import com.google.api.client.http.apache.ApacheGData;

public class AndroidGData {

  // TODO: take advantage of android.net.Uri?

  // TODO: android.util.Log for logging?

  public static final LowLevelHttpTransport HTTP_TRANSPORT =
      ApacheGData.HTTP_TRANSPORT;

  public static Account[] getGoogleAccounts(AccountManager manager) {
    return manager.getAccountsByType("com.google");
  }

}
