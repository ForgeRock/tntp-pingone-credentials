# PingOne Credentials Pair Wallet node

The PingOne Credentials Pair Wallet node lets you pair PingOne digital wallet
credentials with a Ping user ID.

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
    <td><p>ForgeRock Identity Cloud</p></td>
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

This node retrieves `PingOne user ID` from the journey state.

## Configuration

<table>
  <thead>
    <th>Property</th>
    <th>Usage</th>
  </thead>
  <tbody>
    <tr>
      <td>PingOne Service</td>
      <td>Marketplace Service to integrate with PingOne Services
      </td>
    </tr>
  <tr>
    <td>PingOne Wallet Application ID</td>
    <td>Digital Wallet Application ID from PingOne Credentials.</td>
  </tr>
     <tr>
      <td>Digital Wallet Pairing URL delivery method</td>
      <td>If selected, the user is prompted to select the method-- QR Code, Email, or SMS
to deliver the digital wallet.<br>

</td>
    </tr>
     <tr>
      <td>Allow user to choose the URL delivery method</td>
      <td>If enabled, prompt the user to select the URL delivery method.</td>
    </tr>
     <tr>
      <td>Delivery method message</td>
      <td>The message to display to the user, so they can select the method--QRCODE, SMS, or EMAIL, to receive the pairing URL</td>
    </tr>
     <tr>
      <td>QR code message</td>
      <td>The message with instructions to scan the QR code to begin the digital wallet
pairing process.</td>
    </tr>
    <tr>
      <td>Waiting Message</td>
      <td>Localization overrides for the waiting message. This is a map of locale to message.</td>
    </tr>
     <tr>
      <td>Store Wallet Response</td>
      <td>Store the list of user's digital wallet data in the shared state
under a key named `pingOneWallet`.
[NOTE]
The key is empty if the node is unable to retrieve the wallet pairing data
from PingOne service.
</td>
    </tr>
    <tr>
      <td>Submission timeout</td>
      <td>Digital wallet pairing timeout in seconds. 
      </td>
    </tr>
    <tr>
  </tbody>
</table>

## Outputs

<ul>
<li>pingOneWallet - The new PingOne User's digital wallet</li>
</ul>

## Outcomes

`Success`
All configured checks passed.

`Failure`
There was an error during the Pairing process

`Time Out`
The pairing process reached the configured timeout value.

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

