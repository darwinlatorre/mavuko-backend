CREATE TABLE companies (
    id UUID PRIMARY KEY,
    legal_name TEXT NOT NULL,
    tax_identifier TEXT,
    CONSTRAINT companies_legal_name_not_blank CHECK (legal_name ~ '[^[:space:]]')
);
