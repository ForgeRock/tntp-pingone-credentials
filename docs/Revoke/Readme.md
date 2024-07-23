# PingOne Credentials Revoke

Using the PingOne Credentials Revoke node, you can revoke existing PingOne
credentials in a journey.

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

This node retrieves from the journey state:
* **The PingOne User ID**
* **The Credential Type ID**

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
    <tr>
      <td>Credential Id Attribute</td>
      <td>Local attribute name to retrieve the Credential Id Attribute from the journey state.</td>
    </tr>
  </tbody>
</table>

## Outputs

None

## Outcomes

`Success`
All configured checks passed.

`Not Found`
No credential was found.

`Failure`
There was an error during the Revoke process

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

If the API call to PingOne Credentials fails, the following exception will be logged:

* Error: PingOne Credentials Revoke a User's Credential - `Status Code` - `Response Body` 

