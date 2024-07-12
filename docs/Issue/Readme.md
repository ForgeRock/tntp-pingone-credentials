# PingOne Credentials Issue node

The PingOne Credentials Issue node lets you create a PingOne credential in a
journey.

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

This node retrieves `pingOneUserId` from the journey state.

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
    <td>Credential Type ID</td>
    <td>The requested credential name</td>
  </tr>
    </tr>
     <tr>
      <td>Attribute map</td>
      <td>The Key - Value mapping used for associating journey state attributes to
credentials. The `Key` is the PingOne credential attribute, and the `Value` is the
corresponding journey state attribute.</td>
    </tr>

  </tbody>
</table>

## Outputs

`pingOneCredentialId` - The ID of the created credential.


## Outcomes

`Success`
All configured checks passed.

`Failure`
There was an error during the Issue process

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If the API call to PingOne Credentials fails, the following exception will be logged:

* Error: PingOne Credentials Issue a User Credential - <Status Code> - <Response Body> 