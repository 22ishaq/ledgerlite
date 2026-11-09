-- Entries are never edited or deleted. Mistakes get fixed with a reversing
-- transaction, so make the database enforce that too.
create function reject_ledger_entry_change() returns trigger as $$
begin
    raise exception 'ledger_entry is append-only';
end;
$$ language plpgsql;

create trigger ledger_entry_append_only
    before update or delete on ledger_entry
    for each row execute function reject_ledger_entry_change();
