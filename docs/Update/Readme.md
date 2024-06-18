# PingOne Credentials Update

The PingOne Credentials Update node lets administrators update an existing PingOne Credential in a Journey.

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
      <td>Marketplace Service to integrate with PingOne Services
      </td>
    </tr>
  <tr>
    <td>Credential Type Id</td>
    <td>The requested credential name</td>
  </tr>
     <tr>
      <td>PingOne User ID Attribute</td>
      <td>Local attribute name to retrieve the PingOne userID from.  Will look in journey state first, then the local datastore
</td>
    </tr>
     <tr>
      <td>Credential Id Attribute</td>
      <td>Local attribute name to retrieve the Credential Id Attribute from the journey state.
</td>
    </tr>
     <tr>
      <td>Attribute map</td>
      <td>Map Shared State attributes to the Credential. The KEY is the Shared State attribute and the Value is the Credential attribute title.</td>
    </tr>

  </tbody>
</table>

## Outputs

None

## Outcomes

`Success`
All configured checks passed.


`Failure`
There was an error during the Update process

## Troubleshooting

If this node logs an error, review the log messages to find the reason for the error and address the issue
appropriately.

