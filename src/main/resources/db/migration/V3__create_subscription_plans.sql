create table subscription_plans (
    code text collate "C" primary key check (code ~ '^[a-z][a-z0-9_-]*$'),
    display_name text not null check (btrim(display_name,
        U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') <> ''),
    price_cop numeric not null check (price_cop >= 0 and price_cop < 'Infinity'::numeric),
    duration_amount integer not null check (duration_amount > 0),
    duration_unit text not null check (duration_unit in ('DAYS', 'MONTHS')),
    user_limit integer check (user_limit > 0),
    location_limit integer not null check (location_limit > 0)
);

-- Initial offers only. Subsequent maintenance edits rows, never this applied migration.
-- NULL user_limit means unlimited; months are calendar units, not thirty days.
insert into subscription_plans
    (code, display_name, price_cop, duration_amount, duration_unit, user_limit, location_limit)
values
    ('trial', 'Prueba', 0, 15, 'DAYS', 2, 1),
    ('business', 'Negocio', 39900, 1, 'MONTHS', null, 3),
    ('growth', 'Crecimiento', 69900, 1, 'MONTHS', null, 5);
