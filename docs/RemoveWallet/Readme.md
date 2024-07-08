# PingOne Credentials Remove Wallet node

The PingOne Credentials Remove Wallet node lets you create a journey to remove
a paired digital wallet from the PingOne user.

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

This node retrieves `PingOne user ID` and `Digital wallet ID` from the journey
state.

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
    <td>Digital Wallet ID Attribute</td>
    <td>Local attribute name to retrieve the digital wallet ID from the journey state.</td>
    </tr>

  </tbody>
</table>

## Outputs

None

## Outcomes

`Success`
All configured checks passed.

`Not Found`
No digital wallet was found to remove.

`Failure`
There was an error during the wallet removal process.

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If the API call to PingOne Credentials fails, the following exception will be logged:

* Error: PingOne Credentials Delete a Digital Wallet - <Status Code> - <Response Body> 