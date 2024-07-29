# PingOne Credentials Verification

The PingOne Credentials Verification node lets administrators configure a journey to verify a PingOne Credential.

## Compatibility

<table>
  <colgroup>
    <col>
    <col>
  </colgroup>
  <thead>
  <tr>
    <th>Product</th>
    <th>Compatible?</th>
  </tr>
  </thead>
  <tbody>
  <tr>
    <td><p>Advanced Identity Cloud</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  <tr>
    <td><p>ForgeRock Access Management (self-managed)</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  <tr>
    <td><p>ForgeRock Identity Platform (self-managed)</p></td>
    <td><p><span>Yes</span></p></td>
  </tr>
  </tbody>
</table>

## Inputs

If the `Push` delivery method is selected, this node retrieves `pingOneApplicationInstanceId` from journey state.
If `Custom Requested Credentials` is selected, this node retrieves `requestedCredentials` from journey state. 

## Dependencies
This node requires a PingOne Worker Service configuration so that it can connect to your PingOne instance and perform
the PingOne Credentials operations.

## Configuration

<table>
  <thead>
    <th>Property</th>
    <th>Usage</th>
  </thead>
  <tbody>
    <tr>
      <td>PingOne Worker service ID</td>
      <td>The ID of the PingOne Worker service for connecting to PingOne.</td>
    </tr>
    <tr>
      <td>Credential Type</td>
      <td>Type of credential to verify. Must be the name of a PingOne credential type issued by the credential issuer.</td>
    </tr>
    <tr>
      <td>Disclosure Attribute Keys</td>
      <td>Attribute key names for selective disclosure to return from the credential.</td>
    </tr>
    <tr>
      <td>Digital Wallet Application ID</td>
      <td>Digital Wallet Application ID from PingOne Credentials required for Push delivery method.</td>
    </tr>
    <tr>
      <td>Verification URL Delivery Method</td>
      <td>The delivery method for the Verification URL. Choose from: <br>

- QR Code
- Push</td>
    </tr>
    <tr>
      <td>Allow user to choose the URL delivery method</td>
      <td>If enabled, prompt the user to select the URL delivery method.</td>
    </tr>
    <tr>
      <td>Delivery method message</td>
      <td>The message to display to the user allowing them to choose the delivery method to \
  receive the credential verification URL (QRCODE, PUSH).</td>
    </tr>
    <tr>
      <td>QR code message</td>
      <td>The message with instructions to scan the QR code to begin the credential verification process.</td>
    </tr>
    <tr>
      <td>Waiting Message</td>
      <td>Localization overrides for the waiting message. This is a map of locale to message.</td>
    </tr>
    <tr>
      <td>Push Message</td>
      <td>A custom message that the end-user will see when requesting the credential.</td>
    </tr>
    <tr>
      <td>Store Credential Verification Response</td>
      <td>Store the list of verified data submitted by the user in the shared state under a key
  named <code>pingOneCredentialVerification</code>.<br><br>
  <em>Note</em>: The key is empty if the node is unable to retrieve the wallet pairing data from PingOne.</td>
    </tr>
    <tr>
      <td>Verification Timeout</td>
      <td>The period of time (in seconds) to wait for a response to the Verification request. If no
  response is received during this time the node times out and the verification process fails.</td>
    </tr>
    <tr>
      <td>Custom Requested Credentials</td>
      <td>If selected a custom requested credentials payload should be retrieved from the requestedCredentials attribute in shared state</td>
    </tr>
  </tbody>
</table>

## Outputs

`pingOneCredentialVerification`: The new PingOne Credential Verification
request status and full response.
`pingOneApplicationInstanceId`: The identifier of the application running the Wallet SDK on the user's device and registered with the service.

## Outcomes

`Success`
All configured checks passed.

`Error`
There was an error during the Verification process

`Time Out`
The pairing process reached the configured timeout value.

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If any of the API calls to PingOne Credentials fail, the following exceptions will be logged:

* Error: PingOne Credentials Create Verification session - `Status Code` - `Response Body`
* Error: PingOne Credentials Create Push Verification session - `Status Code` - `Response Body`
* Error: PingOne Credentials Read a Verification Session - `Status Code` - `Response Body` 