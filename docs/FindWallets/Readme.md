# PingOne Credentials Find Wallets

The PingOne Credentials Find Wallets node lets you create a journey to list all the paired digital wallets from the PingOne user.

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

This node retrieves `pingOneUserId` from the journey state or from the `objectAttributes` within the journey state.

## Configuration

<table>
  <thead>
    <th>Property</th>
    <th>Usage</th>
  </thead>
  <tbody>
    <tr>
      <td>PingOne Service</td>
      <td>Marketplace Service to integrate with PingOne Services</td>
    </tr>
  </tbody>
</table>

## Outputs

`pingOneWalletId` - The PingOne digital wallet ID.
`pingOneApplicationInstanceId` - The Application Instance ID of the digital wallet where the credential was stored.
`pingOneActiveWallets` - The PingOne User's active digital wallets.`

## Outcomes

`Success`
All digital wallets were found and returned

`Not Found`
No digital wallet was found.

`Failure`
There was an error during the process of finding the wallet.

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If the API call to PingOne Credentials fails, the following exception will be logged:

* Error: PingOne Credentials Find a Wallet - `Status Code` - `Response Body` 