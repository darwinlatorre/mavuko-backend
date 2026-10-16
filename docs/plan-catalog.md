# Maintain the commercial plan catalog

Prices, display names, durations and user/location quotas live in PostgreSQL
`subscription_plans`, not Java constants. V3 inserts the initial offers once.
The internal `PlanCatalogService` uses the existing `persistence` profile and
read-only application transactions. Each new service call reads current data;
there is no static fallback, startup snapshot or catalog cache.

## Maintenance

Use an explicitly authorized database session and a transaction to edit a row by
its stable `code`. Check the affected row and commit only the intended change.
For example, using **illustrative values**, not a new price decision:

```sql
begin;
update subscription_plans set price_cop = 45000 where code = 'business';
select * from subscription_plans where code = 'business';
-- Commit after checking the result; otherwise roll back.
```

`price_cop` is an exact, finite, nonnegative decimal in COP. No rounding or billing
formula is introduced. Durations store a positive amount and `DAYS` or `MONTHS`;
one month is not converted to thirty days. `user_limit = NULL` means unlimited;
non-null user quotas and location quotas must be positive. Names cannot be blank.
New offers can be inserted without changing a Java plan enumeration.

Do not edit applied migrations or reseed during startup. Existing subscriptions
must retain their contracted terms when that workflow is implemented: this table
holds current offers only. V3 neither changes companies/access grants nor creates
subscriptions, payments or automatic activation.

No administrative UI or HTTP editing endpoint is included yet. Database permissions
control maintenance; HTTP remains closed. Without `persistence`, no catalog service
or in-memory substitute is registered. Do not drop this table as an automatic rollback.

Verify against disposable PostgreSQL with `mvn -B -ntp -Pintegration-tests verify`.
