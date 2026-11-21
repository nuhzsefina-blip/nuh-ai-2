const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_judge_123";
const BOB_UID = "bob_judge_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCasePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    caseNumber: "31513",
    caseType: "APPEAL",
    ethiopianDate: "17/09/2018",
    gregorianDate: "2026-05-25",
    judges: "ኑህ መሀመድ",
    plaintiffName: "አቶ እድሜዓለም ተሰራ ካሳ",
    plaintiffCounsel: "አንድነት አስራት",
    defendantName: "ተስፋዬና ጓደኞቹ የህ/ሽ/ማህበር",
    defendantCounsel: "",
    unifiedDocumentText: "የመ/ቁ:- 31513\nፍ ር ድ\nተመርምሮ ተወስኗል።",
    judgeNotesText: "",
    status: "DRAFT",
    qualityRating: "NONE",
    badReasonCategory: "NONE",
    badReasonNote: "",
    currentVersion: 1,
    contentSha256: "abc123def456",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validResearchNotePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    noteType: "SEARCH",
    title: "የይግባኝ ጣልቃ ገብነት",
    content: "የፍ/ብ/ሥ/ሥ ሕግ አንቀጽ 41 መሰረት ጣልቃ ገብነት።",
    sources: ["https://chilot.me/civil-procedure"],
    caseNumber: "31513",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied from reading or writing users, cases, and research_notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cases").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("research_notes").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_1").set(validCasePayload(ALICE_UID))
  );
});

test("Authenticated owner: user profile create/update & cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "ዳኛ ኑህ መሀመድ",
      email: "nuh@example.com",
      courtName: "የሐረሪ ብሔራዊ ክልል ከፍተኛ ፍርድ ቤት",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update on displayName succeeds while createdAt remains immutable
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "ዳኛ ኑህ",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or modify Alice's profile (PII protection)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijack" }));

  // Ghost field rejected (Shadow Update Test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cases: valid Kotlin create, partial update, terminal FINALIZED lock, and negative guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid create (no @DocumentId `id` key)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").set(validCasePayload(ALICE_UID))
  );

  // @DocumentId `id` field must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_with_id").set({
      id: "case_with_id",
      ...validCasePayload(ALICE_UID),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_future").set(
      validCasePayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Invalid enum rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_bad_enum").set(
      validCasePayload(ALICE_UID, { status: "finalized" })
    )
  );

  // Owner partial update to GOOD grade succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").update({
      qualityRating: "GOOD",
      currentVersion: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read, list, or write Alice's cases
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cases").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").update({
      unifiedDocumentText: "tampered",
    })
  );

  // Alice finalizes case -> becomes terminal Read-Only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").update({
      status: "FINALIZED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Subsequent update or delete on FINALIZED case fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").update({
      unifiedDocumentText: "illegal post-final edit",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_31513").delete()
  );

  // Replicating Kotlin repository list query on /users/{uid}/cases
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").orderBy("updatedAt", "desc").get()
  );
});

test("Research notes: create, bounded list, query, update, delete, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("research_notes")
      .doc("note_1")
      .set(validResearchNotePayload(ALICE_UID))
  );

  // Oversized sources array (>20) must fail
  const tooManySources = Array.from({ length: 21 }, (_, i) => `https://example.com/${i}`);
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("research_notes")
      .doc("note_too_many")
      .set(validResearchNotePayload(ALICE_UID, { sources: tooManySources }))
  );

  // Replicating Kotlin repository query
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("research_notes").orderBy("updatedAt", "desc").get()
  );

  // Update mutable fields succeeds; mutating immutable noteType fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("research_notes").doc("note_1").update({
      title: "የተሻሻለ ማስታወሻ",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("research_notes").doc("note_1").update({
      noteType: "TRANSCRIPT",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Cross-user read & delete denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("research_notes").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("research_notes").doc("note_1").delete());

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("research_notes").doc("note_1").delete()
  );
});
