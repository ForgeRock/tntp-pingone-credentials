<!--
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services. Ping Identity Corporation only offers such software or services to legal entities who have entered into a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
-->

# PingOne Credentials Nodes

The PingOne Credentials nodes utilizes the PingOne Credentials service to implement Digital Wallet pairing, Credential 
management, and Credential Verification:

* [Introduction to PingOne Credentials](https://docs.pingidentity.com/r/en-us/pingone/pingone_credentials_introduction_to_pingonecredentials)

> At this time, no other PingOne Verification is supported by these nodes.

Identity Cloud provides the following artifacts to enable the PingOne Verify Nodes:

* [PingOne service](https://github.com/ForgeRock/tntp-ping-service/tree/cloudprep?tab=readme-ov-file#ping-one-service)
* [PingOne Verify Pair Wallet node](https://github.com/ForgeRock/tntp-pingone-credentials/blob/main/docs/PairWallet/Readme.md)
* [PingOne Verify Remove Wallet node](https://github.com/ForgeRock/tntp-pingone-credentials/blob/main/docs/RemoveWallet/Readme.md)
* [PingOne Verify Remove Wallet node](https://github.com/ForgeRock/tntp-pingone-credentials/blob/main/docs/FindWallets/Readme.md)

You must set up the following before using the PingOne Verify nodes:

* [Create a credential](https://docs.pingidentity.com/r/en-us/pingone/pingone_creating_and_managing_credentials)
* [Create a worker application](https://docs.pingidentity.com/r/en-us/pingone/p1_add_app_worker)
  * Requires [Identity Data Admin](https://apidocs.pingidentity.com/pingone/platform/v1/api/#roles) role
* [PingOne service](https://github.com/ForgeRock/tntp-ping-service/tree/cloudprep?tab=readme-ov-file#ping-one-service)

For more information on these nodes, refer to PingOne Credentials nodes
