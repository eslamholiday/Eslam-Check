# Eslam Check Exchange Language (ECX) v1

ECX is the stable bridge between a Best Choice statement and the Eslam Check Android app.

## Envelope

```
ECX|1
B|<batch_id>|BESTCHOICE|<CUMULATIVE|DELTA>
L|<USD|IQD>|<range_from>|<range_to>|<opening_date>|<opening_balance>|<closing_balance>|<transaction_count>
...records...
Z|<usd_transaction_count>|<iqd_transaction_count>
```

A text may contain USD only, IQD only, or both ledgers.

## Transaction record

```
T|CUR|SEQ|OP|DATE|SRC|AMOUNT|EFFECT|DISCOUNT|BALANCE|PNR|ROUTE|FLAGS|
```

- CUR: USD or IQD
- SEQ: row sequence inside this import only. It is not identity.
- OP: Best Choice operation number.
- DATE: YYYY-MM-DD.
- SRC:
  - TS = Sale Tickets
  - VS = Sale Visa / Visa Sale
  - TC = TicketOperation Change
  - TNC = TicketOperation New Change
  - TR = TicketOperation Refund
  - TNR = TicketOperation New Refund
  - PAY = Receipt Voucher / ID Voucher Receipt
  - UNK = source not safely classified
- AMOUNT: absolute transaction amount as printed.
- EFFECT: signed ledger effect. Sale/change/visa are normally positive; refund/payment normally negative; zero stays 0.
- DISCOUNT: source Discount only. Blank means not present.
- BALANCE: balance printed after that row.
- PNR/ROUTE: blank if not printed.
- FLAGS: comma separated.
- Final field is reserved and must remain blank in ECX v1.

## Person/item record

```
P|CUR|OP|NAME|PTYPE|DOC|VALUE|PASSPORT|PRODUCT|FLAGS
```

For tickets:
- PTYPE = ADT, CHD or INF
- DOC = ticket number when printed
- VALUE = passenger ticket value

For visas:
- PTYPE and DOC are usually blank
- VALUE = per-person visa value
- PASSPORT = passport number when printed
- PRODUCT = source visa description when useful

For change/refund:
- NAME and DOC may be stored when printed
- VALUE may remain blank because the parent transaction carries the amount

## Note record

```
N|CUR|OP|SOURCE_NOTE
```

Use only for a note actually present in the statement.

## Flags

- CANCEL = source explicitly says cancellation (for example الغاء / إلغاء)
- ZERO = amount is zero
- UNCLEAR = source cannot be extracted safely
- NO_ROUTE = ticket route not printed
- NO_PNR = PNR not printed
- VOID = explicit void
- REISSUE = explicit reissue

Do not infer CANCEL/VOID/REISSUE unless the source explicitly supports it.

## Identity and change detection

The operation identity is `currency + operation number`.

SEQ and BALANCE are import-snapshot fields and are NOT part of the operation identity. Older cumulative statements can reorder same-day rows, which can change the displayed running balance without changing the underlying operation.

A reviewed operation is considered materially changed only when core fields change, such as amount, discount, date, PNR, route, type/source, or passenger set.

## Text rules

- Numeric fields use ASCII digits and a dot for decimals.
- Thousands separators are optional; generated ECX should omit them.
- Names may be Arabic or English.
- Route is normalized with hyphens, e.g. BGW-AMM-BGW.
- PNR is uppercase.
- A literal pipe inside text must be escaped as \\|.
- Lines beginning with # or // are comments and ignored.
- Missing optional values are represented by an empty field, never invented.
