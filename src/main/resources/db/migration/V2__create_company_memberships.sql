create table company_memberships (
    issuer text collate "C" not null,
    subject text collate "C" not null,
    company_id uuid not null references companies(id),
    primary key (issuer, subject, company_id),
    -- Match Java 21 Character.isWhitespace, including Unicode separators.
    -- Trimming is validation only: stored identity components remain unchanged.
    constraint company_memberships_nonblank_identity check (
        btrim(issuer, U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') <> ''
        and btrim(subject, U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') <> ''
    )
);

create index company_memberships_company_id_idx on company_memberships(company_id);
