# ChatGPT -> Eslam Check conversion contract — ECX v3 / K1

Use this contract whenever the user sends Best Choice / Taj Al-Yamama statements.

## Automatic behavior

When the user sends the USD and IQD PDFs with no additional text, treat that as an instruction to:

1. read both statements;
2. compare them with previously known cumulative statements when available;
3. apply the agreed translation rules;
4. produce one combined ECX v3 / K1 import text or file;
5. ask only about genuinely new ambiguous patterns.

Do not ask the user to repeat this instruction.

## Source discipline

Extract only what the PDFs support. Never invent PNRs, amounts, routes, passengers, ticket numbers, passports, notes or balances.

Business rules explicitly taught by the user may normalize type, Visa country and airline. Do not calculate Base Fare, expected commission, taxes or profit inside ECX.

## IDs

Account code is always `TY`.

Derived ledgers:

- IQD: `BC-TAJALYAMAMA-IQD`
- USD: `BC-TAJALYAMAMA-USD`

Derived operation ID:

`<ledger>-<operation number>`

DOC_ID and SNAPSHOT_ID must be deterministic from canonical source content, not file name.

Recommended source hash form:

`D<first 16 uppercase hex chars of SHA-256(canonical statement content)>`

The exact same source must produce the exact same ID again.

## ECX v3 shape

```
X3|K1|TY|DOC_ID|SNAPSHOT_ID|C
L|I|FROM|TO|OPEN_DATE|OPEN|CLOSE|COUNT
L|U|FROM|TO|OPEN_DATE|OPEN|CLOSE|COUNT
O|CUR|OP|DATE|TYPE|AMOUNT|BALANCE|PNR|ROUTE|AIR|VISA|DISCOUNT|EFFECT|FLAGS
Q|CUR|OP|NAME|PTYPE|DOC|VALUE|PASSPORT|PRODUCT|FLAGS
M|CUR|OP|SOURCE_NOTE
H|USD_COUNT|IQD_COUNT|PERSON_COUNT|CHECKSUM
```

## Type normalization

- Sale Tickets -> T
- Change / New Change -> C
- Refund / New Refund -> R
- Visa Sale -> V
- Receipt Voucher / ID Voucher Receipt -> P
- Sale Tickets value 0 under the agreed Taj Al-Yamama pattern -> X
- Visa value 0 -> X
- explicit Reissue -> E
- genuinely unclear -> ?

Special learned cases:
- operation 15977 is Void.
- operation 46148 is Change.

These examples confirm the rule patterns; do not hard-code only those operation numbers.

## Discount translation

Ticket: preserve source Discount when printed.

Visa: leave Discount blank.
Change: leave Discount blank.
Refund: leave Discount blank.
Payment: leave Discount blank.
Cancelled Visa/Void: leave Discount blank unless a future explicit business rule says otherwise.

The app may store legacy zero values from older ECX, but new v3 output must omit meaningless zero Discount fields.

## Airlines

All normal Ticket sales in the IQD statement are Iraqi Airways -> `IA`.

Do not infer USD airline from currency or PNR.

Use an airline code only when source evidence or an agreed mapping identifies it. Known K1 codes are documented in ECX_PROTOCOL.md. Unknown USD airline = blank AIR field.

Ticket prefix 073 is an agreed Iraqi Airways identifier when applicable.

## Visa normalization

From source wording:

- VISA UAE FZ -> AE
- VISA JORDAN RJ / RJ JORDAN VISA -> JO
- VISA EGYPT -> EG
- فيزا السعودية -> SA

Do not infer Visa country from price.

A multi-person Visa stays one O record plus multiple Q records.

## Passenger records

Preserve:
- name
- ADT/CHD/INF as A/C/I
- ticket/document number
- per-person value
- passport when printed
- product text when useful for Visa normalization

Do not invent Base Fare.

## Route and PNR

Normalize route using hyphens, e.g. `BGW-BEY-BGW`.

PNR uppercase.

Reverse commission logic belongs to the app and its commission rules, not the converter.

## Counts and checksum

Ledger COUNT must equal O count for that currency.

H USD/IQD counts must match O records.

H person count must match Q records.

Checksum is SHA-256 first 12 uppercase hex characters of all normalized non-comment lines before H, joined with LF.

## Response style

When conversion succeeds, keep prose short. Give the import file/text immediately. Mention only new ambiguities that require teaching.

The user should not have to ask “translate it” every time they upload the two statements.
