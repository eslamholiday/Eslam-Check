# ChatGPT -> Eslam Check conversion contract

When the user sends Best Choice PDF statements and asks for Eslam Check data:

1. Read the PDF(s) as source documents.
2. Return ECX v1 data only when the user asks for the import text.
3. Do not calculate commission, profit, expected settlement, airline rules, or business correctness.
4. Extract source facts only:
   - currency ledger
   - operation number
   - date
   - source operation label
   - transaction amount
   - debit/credit effect
   - running balance
   - PNR
   - route
   - Discount
   - passenger/person names
   - passenger type
   - ticket/document number when printed
   - per-person value
   - passport and visa source description
   - explicit source note
5. Normalize source labels only to ECX SRC codes. Do not reinterpret business meaning beyond that mapping.
6. Sale Ticket with amount 0:
   - add ZERO
   - do not call it VOID or REISSUE unless the source explicitly says so
7. Visa with amount 0:
   - add ZERO
   - add CANCEL only when the source explicitly states cancellation
8. New Change stays SRC=TNC. New Refund stays SRC=TNR. The app normalizes them to Change/Refund while preserving the original source code.
9. If a field is missing, leave it blank. Never guess.
10. If extraction is uncertain, preserve what is certain and add UNCLEAR.
11. Keep USD and IQD inside the same ECX envelope when both are supplied.
12. Operation order is preserved as SEQ for the current statement, but identity is currency + operation number.
13. Balance changes caused only by cumulative row reordering are not a material operation change.
14. The L count and Z counts must exactly match the number of T records generated.
15. Never output markdown inside the ECX copy block itself.

The preferred user-facing response for a conversion is:
- one short sentence
- one copyable ECX code block
- a short warning only if one or more records carry UNCLEAR
