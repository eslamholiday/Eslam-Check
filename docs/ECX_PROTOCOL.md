# Eslam Check Exchange Language — ECX v3 / K1

ECX v3 is the compact copy/paste language between ChatGPT and Eslam Check.

The Android app continues to accept ECX v1 and ECX v2. New conversions must use v3.

## Goals

- Keep the copied text short.
- Keep stable IDs across cumulative PDFs.
- Never repeat long words such as TAJALYAMAMA, PAYMENT, DISCOUNT, ROUTE, AIRLINE.
- Preserve source facts exactly.
- Let the app expand short codes into normal Arabic/English labels.
- Detect incomplete or altered pasted text with a checksum.

## Header

```
X3|K1|TY|<DOC_ID>|<SNAPSHOT_ID>|C
```

- `X3` = ECX v3.
- `K1` = dictionary version 1.
- `TY` = Taj Al-Yamama account.
- `DOC_ID` = deterministic source-document identity.
- `SNAPSHOT_ID` = deterministic cumulative snapshot identity.
- `C` = cumulative, `D` = delta.

The app expands `TY` to Taj Al-Yamama internally.

## Currency codes

- `I` = IQD
- `U` = USD

## Ledger record

```
L|<I/U>|<FROM>|<TO>|<OPEN_DATE>|<OPEN_BAL>|<CLOSE_BAL>|<COUNT>
```

Ledger IDs are derived automatically:

- `TY + I` -> `BC-TAJALYAMAMA-IQD`
- `TY + U` -> `BC-TAJALYAMAMA-USD`

## Operation record

```
O|CUR|OP|DATE|TYPE|AMOUNT|BALANCE|PNR|ROUTE|AIR|VISA|DISCOUNT|EFFECT|FLAGS
```

Type codes:

- `T` = Ticket
- `V` = Visa
- `C` = Change
- `R` = Refund
- `P` = Payment
- `X` = Void / cancellation
- `E` = Reissue
- `F` = Fee
- `?` = genuinely unclear

The stable operation ID is not copied in full. The app derives it automatically:

`BC-TAJALYAMAMA-<CURRENCY>-<OP>`

Example:

```
O|I|57336|2026-09-21|T|1117406|3681539|78TYQ2|BGW-BEY-BGW|IA||61459|1117406|
```

The app expands this to the same stable ID every time operation 57336 appears in future cumulative statements.

### Discount rule in v3

The DISCOUNT field is populated only when it is meaningful for a Ticket.

For Visa, Change, Refund, Payment and cancelled Visa, leave the field blank. Do not write zero merely to fill the field.

## Passenger/person record

```
Q|CUR|OP|NAME|PTYPE|DOC|VALUE|PASSPORT|PRODUCT|FLAGS
```

Passenger type:

- `A` = Adult / ADT
- `C` = Child / CHD
- `I` = Infant / INF

Ticket example:

```
Q|I|57336|Alwawi Safaa|A|0732410022174|589433|||
```

Visa example:

```
Q|U|55975|ALI SAADOON|||75|A19015392|VISA UAE FZ|
```

Passport values may be preserved internally when printed in the source, but the normal Visa review UI does not display them. Passport files are managed separately inside the passenger profile.

## Source note

```
M|CUR|OP|SOURCE_NOTE
```

Use only for notes actually printed by the source.

## Visa country codes

- `AE` = UAE
- `JO` = Jordan
- `EG` = Egypt
- `SA` = Saudi Arabia

Price never determines the Visa country.

## Airline codes — K1

The app shows full airline names. The copied text may use these short codes:

- `IA` = Iraqi Airways
- `G9` = Air Arabia
- `FZ` = Flydubai
- `PC` = Pegasus
- `TK` = Turkish Airlines
- `QR` = Qatar Airways
- `EK` = Emirates
- `RJ` = Royal Jordanian
- `IF` = Fly Baghdad
- `ME` = Middle East Airlines
- `OV` = SalamAir
- `XY` = Flynas
- `GF` = Gulf Air
- `MS` = EgyptAir
- `NP` = Nile Air
- `RB` = Fly Cham

Unknown USD airline: leave AIR blank. It remains a Ticket and the app shows “الخط مبهم”.

Additional airlines can be added later to the app airline catalog.

## Footer and checksum

```
H|<USD_COUNT>|<IQD_COUNT>|<PERSON_COUNT>|<CHECKSUM>
```

Checksum algorithm:

1. Normalize line endings to LF.
2. Trim each line.
3. Remove blank lines and comment lines beginning with `#` or `//`.
4. Take every normalized line before the `H` line.
5. Join with `\n`.
6. SHA-256.
7. Use the first 12 hexadecimal characters, uppercase.

A mismatched checksum blocks import.

## Agreed business translations

- TicketOperation Change and New Change -> `C`.
- TicketOperation Refund and New Refund -> `R`.
- Sale Tickets with note `تغيير` -> `C`.
- Zero-value Sale Tickets under the agreed Taj Al-Yamama rule -> `X`.
- Zero-value Visa -> `X` and cancelled Visa.
- Receipt Voucher / ID Voucher Receipt -> `P`.
- All normal Ticket sales in the IQD statement -> Iraqi Airways (`IA`).
- USD Iraqi Airways may be set to `IA` when the agreed ticket/source mapping proves it.
- Other unknown USD airlines stay blank.
- Percentage commissions are never calculated by ChatGPT. Base Fare and commission math are app-side.
- Visa type comes from source wording, never the price.
- Multiple Visa passengers remain one operation with multiple Q records.

## Stable identity

Re-uploading the same PDF must produce the same DOC_ID and SNAPSHOT_ID.

A later cumulative PDF with additional data gets a new DOC_ID/SNAPSHOT_ID, but repeated operations keep the same derived OP_ID.

Sequence and running balance are snapshot facts, not identity.
