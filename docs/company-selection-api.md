# List the authenticated user's companies

With `persistence` and valid Auth0 issuer/audience configuration, send:

```http
GET /api/v1/companies
Authorization: Bearer <API access token>
```

The JSON array contains only `id` (UUID) and `legalName` for explicit memberships
matching the validated token's exact `iss` and `sub`. Results retain the repository's
legal-name/ID ordering. An identity without grants receives `[]`; email, token role
claims and global administrator claims never supply implicit memberships.

This is a company selector, not permission to operate on a company. It does not
expose tax identifiers, provision users, grant access or activate subscriptions.
There are no query parameters or client identity selectors. All query parameters
are rejected; pagination is not implemented in this initial endpoint.

| Request | Result |
| --- | --- |
| Valid API token, explicit memberships | 200, selector array, `Cache-Control: no-store` |
| Valid API token, no memberships | 200, `[]` |
| Missing/invalid authentication | 401, Bearer challenge |
| Authenticated request with query parameters | 400, no data |
| Membership persistence unavailable | 503, no data; not a false empty list |
| Other methods, company-detail or administrative routes | Still denied |

No login redirect, session or authentication cookie is created. Without `persistence`,
the controller is absent and the previous closed authorization boundary remains.
The existing configured issuer/audience validation is unchanged; no actual Auth0
resource is provisioned by this code change.

The integration regression uses real local HTTP, signed test tokens, mocked local
JWKS responses and disposable PostgreSQL. Run `mvn -B -ntp -Pintegration-tests verify`.
