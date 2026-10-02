# Eslam Check Exchange Language (ECX) v2

ECX v2 is the stable bridge between ChatGPT and the Eslam Check Android app.

The app still accepts ECX v1 for backward compatibility, but new statement conversions should use v2.

## Core identity rules

Three source identities are carried by the bridge:

- `DOC_ID`: deterministic identity of the PDF/source content. Re-sending the same PDF, even with a different file name, must produce the same DOC_ID.
- `SNAPSHOT_ID`: identity of that cumulative statement snapshot. For an identical source it stays identical.
- `OP_ID`: stable identity of one Best Choice operation. The same operation appearing again in later cumulative PDFs must keep the same OP_ID.

The preferred current ledgers are:

- `BC-TAJALYAMAMA-USD`
- `BC-TAJALYAMAMA-IQD`

Preferred operation identity:

`<LEDGER_ID>-<BEST_CHOICE_OPERATION_NUMBER>`

Example:

`BC-TAJALYAMAMA-IQD-3417`

Sequence and running balance are snapshot information only and never create a new operation identity.

## Envelope

```
ECX|2
B|<snapshot_id>|BESTCHOICE|<CUMULATIVE|DELTA>|<doc_id>
L|<ledger_id>|<USD|IQD>|<range_from>|<range_to>|<opening_date>|<opening_balance>|<closing_balance>|<transaction_count>
...records...
Z|<usd_transaction_count>|<iqd_transaction_count>
```

A single ECX text may contain USD only, IQD only, or both.

## Transaction record

```
T|OP_ID|CUR|SEQ|OP|DATE|SRC|AMOUNT|EFFECT|DISCOUNT|BALANCE|PNR|ROUTE|AIRLINE|VISA_COUNTRY|FLAGS|
```

Fields:

- `OP_ID`: stable operation identity.
- `CUR`: USD or IQD.
- `SEQ`: row order inside the current snapshot only.
- `OP`: Best Choice operation number.
- `DATE`: YYYY-MM-DD.
- `SRC` normalized business type:
  - `TK` = Ticket
  - `VI` = Visa
  - `CH` = Change, including New Change
  - `RF` = Refund, including New Refund
  - `PAY` = Receipt Voucher / payment
  - `VO` = Void / cancelled item according to the agreed rules
  - `REI` = Reissue when explicitly known
  - `UNK` = cannot be classified safely
- `AMOUNT`: operation amount printed in the statement.
- `EFFECT`: signed ledger effect.
- `DISCOUNT`: source Discount only.
- `BALANCE`: running balance printed after the row.
- `PNR`, `ROUTE`: blank when absent.
- `AIRLINE`: airline only when known by an agreed rule/source. For an unknown USD ticket leave blank; the app shows “الخط مبهم”.
- `VISA_COUNTRY`: normalized country when identifiable from the visa product, e.g. UAE, JORDAN, EGYPT, SAUDI.
- `FLAGS`: comma-separated flags.
- final field is reserved and must remain blank.

## Person/item record

For v2, person records keep the v1-compatible shape:

```
P|CUR|OP|NAME|PTYPE|DOC|VALUE|PASSPORT|PRODUCT|FLAGS
```

Ticket fields:

- PTYPE = ADT / CHD / INF
- DOC = ticket number when printed
- VALUE = passenger value printed by the source
- Base Fare is not invented by ChatGPT; the user enters it in the app when required.

Visa fields:

- VALUE = per-person visa value
- PASSPORT = passport number
- PRODUCT = source visa description

Change/refund:

- NAME and DOC are preserved when printed.

## Note record

```
N|CUR|OP|SOURCE_NOTE
```

Only source notes actually printed in the statement are carried.

## Agreed Best Choice translation rules

- Change and New Change normalize to `CH`.
- Refund and New Refund normalize to `RF`.
- A Sale Tickets row with source note `تغيير` normalizes to `CH`.
- A Sale Tickets row with zero value, under the agreed Taj Al-Yamama rule, normalizes to `VO`.
- A zero-value Visa normalizes to `VO` / cancelled visa.
- Visa country is normalized from the printed visa product; price never determines visa type.
- All ticket sales in the IQD statement are classified as `Iraqi Airways`.
- Iraqi Airways may rarely appear in USD; when the agreed ticket prefix/source identifies it, AIRLINE may be filled.
- Other unknown USD airlines remain blank and are selected inside the PNR screen.
- Percentage commission is never calculated in ECX. The app calculates it from Base Fare.
- Fixed issuance fees are not calculated in ECX.

## Identity and cumulative statements

When a later cumulative statement repeats an operation:

- the OP_ID stays identical;
- the new PDF gets its own DOC_ID/SNAPSHOT_ID if its content changed;
- if core operation facts are unchanged, the app treats it as the same operation;
- changing only SEQ or BALANCE does not mark the operation materially changed;
- amount, Discount, date, PNR, route, type, or passenger-set changes can trigger “changed after review”.

## Text rules

- ASCII digits and decimal dot.
- Omit thousands separators in generated ECX.
- Route uses hyphens: `BGW-AMM-BGW`.
- PNR uppercase.
- Literal pipe in text is escaped as `\|`.
- Missing optional values are empty fields; never invent them.
- Lines beginning with `#` or `//` are comments and ignored.
