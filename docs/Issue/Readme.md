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

This node retrieves `pingOneUserId` from the journey state, also it will retrieve  
the shared state attributes defined in the Attribute map.  


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
      <td>PingOne UserID Attribute</td>
      <td>Local attribute name to retrieve the PingOne userID from.  Will look in journey state first, then the local datastore</td>
    </tr>
    <tr>
      <td>Credential Type ID</td>
      <td>The requested credential name</td>
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

`Error`
There was an error during the Issue process

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If the API call to PingOne Credentials fails, the following exception will be logged:

* Error: PingOne Credentials Issue a User Credential - `Status Code` - `Response Body` 