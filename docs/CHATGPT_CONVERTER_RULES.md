# ChatGPT -> Eslam Check conversion contract

Use this contract whenever the user sends Best Choice / Taj Al-Yamama PDF statements for Eslam Check.

## Output version

New conversions must use **ECX v2**. The Android app remains backward-compatible with ECX v1.

## Source discipline

1. Read the PDF(s) as the source.
2. Extract source facts exactly; never invent a passenger, amount, route, PNR, passport, ticket number, note, or balance.
3. Do not calculate commission, profit, expected settlement, Base Fare, or taxes inside ECX.
4. Business rules that the user explicitly established may be used only for normalization fields such as operation type, airline, and visa country.
5. If extraction is uncertain, preserve what is certain and add `UNCLEAR`.

## Stable IDs

Every conversion must create deterministic IDs.

### LEDGER_ID

Use:

- `BC-TAJALYAMAMA-USD`
- `BC-TAJALYAMAMA-IQD`

### OP_ID

For every operation:

`<LEDGER_ID>-<operation number>`

The same operation in every later cumulative PDF must always get the same OP_ID.

### DOC_ID and SNAPSHOT_ID

Build DOC_ID deterministically from canonical statement content, not from the uploaded file name.

Canonical identity material should include, in sorted ledger order:

- ledger ID
- statement range
- opening balance
- closing balance
- complete ordered set of normalized operation core facts and passenger/item facts

Hash that canonical material with SHA-256 and use a short stable form such as:

`DOC-<first 16 uppercase hex chars>`

Use the same value as SNAPSHOT_ID unless a separate snapshot identity is required.

Re-uploading the exact same PDF or the same statement with only a renamed file must produce the same DOC_ID/SNAPSHOT_ID.

## What to extract

For every operation, when printed:

- currency ledger
- operation number
- date
- source label
- operation amount
- debit/credit ledger effect
- running balance
- PNR
- route
- Discount
- passengers/persons
- passenger type
- ticket/document number
- per-person value
- passport
- visa source description
- explicit source note

## Normalized operation rules agreed with the user

Use these ECX v2 SRC values:

- `TK` Ticket
- `VI` Visa
- `CH` Change
- `RF` Refund
- `PAY` Payment
- `VO` Void/cancelled
- `REI` Reissue
- `UNK` genuinely unclear

Apply the following user-specific translation rules:

1. `TicketOperation Change` and `TicketOperation New Change` => `CH`.
2. `TicketOperation Refund` and `TicketOperation New Refund` => `RF`.
3. `Sale Tickets` with source note `تغيير` => `CH`.
4. A zero-value `Sale Tickets` operation in these Taj Al-Yamama statements => `VO` and add flags `ZERO,VOID`.
5. A zero-value Visa => `VO`, add `ZERO,CANCEL`.
6. Receipt Voucher / ID Voucher Receipt => `PAY`.
7. Do not preserve New Change/New Refund as a separate app type.

## Airline normalization

1. Every normal Ticket in the **IQD statement** => `AIRLINE=Iraqi Airways`.
2. Iraqi Airways may rarely occur in USD. If the agreed source/ticket prefix identifies Iraqi Airways (currently prefix `073`), AIRLINE may be set to `Iraqi Airways`.
3. For other USD tickets, if airline is not explicitly known from an agreed mapping, leave AIRLINE blank.
4. Never infer airline from PNR alone.
5. Never infer an airline solely because a ticket is in USD.
6. Unknown USD airline is still `SRC=TK`; only its airline is ambiguous.

## Visa normalization

Normalize the visa country from printed product text, never from price:

- `VISA UAE FZ` => `UAE`
- `VISA JORDAN RJ` or `RJ JORDAN VISA` => `JORDAN`
- `VISA EGYPT` => `EGYPT`
- `فيزا السعودية` => `SAUDI`

Visa prices change historically. Preserve each printed per-person value.

A multi-person Visa remains one parent operation with multiple P records.

## Ticket values and Discount

- P.VALUE is the passenger value printed by the statement.
- T.DISCOUNT is the total source Discount printed for the operation.
- Discount is the commission actually deducted from the ticket total in percentage-commission cases, based on the user’s workflow.
- Do not split source Discount among passengers unless the PDF explicitly provides per-passenger values.
- Do not invent Base Fare. The app collects Base Fare per passenger and calculates expected commission there.
- Percentage commissions are always applied to Base Fare, same percentage for ADT/CHD/INF.
- Fixed issuance fees are per passenger and remain app-side rules.

## Route / reverse

- Normalize route with hyphens.
- Reverse is a business-rule concept handled by the app/rule setup.
- The known special example is departure **BEY -> BGW**; BGW -> BEY and BGW -> BEY -> BGW are not reverse.
- Do not change source route to force a commission rule.

## Counts and validation

- L transaction count must equal the number of T records for that ledger.
- Z counts must equal the number of T records by currency.
- P/N records must point to an existing operation.
- Keep USD and IQD in one ECX envelope when both PDFs are provided.
- Do not use SEQ or running BALANCE as operation identity.

## Preferred user-facing conversion response

When the user asks to convert a new statement:

1. one short sentence with what was recognized;
2. one copyable ECX v2 code block;
3. only list genuinely new/uncertain translation questions, if any.

Do not repeat questions for rules already learned.
