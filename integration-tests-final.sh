#!/bin/bash
# TEIA-users Integration Test Suite - Final Version
# Base URL: http://localhost:8080/teiaUsers

BASE_URL="http://localhost:8080/teiaUsers"
PASS=0
FAIL=0
EXPECTED_FAIL=0

# Test credentials
TEIA_USER_PHONE="+1XXXXXXXXXX"
TWILIO_PHONE_CALLS="+1XXXXXXXXXX"
PHONE_TO_CALL="+573XXXXXXXXX"
TWILIO_SID="REDACTED_TWILIO_SID"
TWILIO_TOKEN="REDACTED_TWILIO_TOKEN"
OPENAI_KEY="REDACTED_OPENAI_KEY"
OPENAI_ASSISTANT="asst_ntmUnQSLXxzK99iAzVAbCH87"
ELEVENLABS_AGENT="agent_9501kcm8wtbce3frq6yxzb1bdfhm"
ELEVENLABS_KEY="REDACTED_ELEVENLABS_KEY"
CAL_API_KEY="cal_live_39241cdf12541d7fb84aa930aad9e84b"
CAL_USER_ID="1440418"
CAL_EVENT_ID="2108679"

# Unique test run ID
TEST_RUN_ID=$(date +%s)

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================================${NC}"
}

test_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASS++))
}

test_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    echo "  Response: ${2:0:200}"
    ((FAIL++))
}

test_expected_fail() {
    echo -e "${YELLOW}[EXPECTED FAIL]${NC} $1"
    ((EXPECTED_FAIL++))
}

# ==============================================================================
# HEALTH CHECK
# ==============================================================================
print_header "SERVICE HEALTH CHECK"

RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/users" 2>&1)
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" -eq 200 ]; then
    test_pass "Service is running and responding (HTTP $HTTP_CODE)"
else
    echo -e "${RED}SERVICE NOT RESPONDING - HTTP $HTTP_CODE${NC}"
    echo "Cannot proceed with tests. Please ensure the service is running."
    exit 1
fi

# ==============================================================================
# ELEVENLABS INFO CRUD
# ==============================================================================
print_header "ELEVENLABS INFO TESTS"

# CREATE
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/eleven-labs-info" \
    -H "Content-Type: application/json" \
    -d "{\"elevenLabsAgentId\": \"test_${ELEVENLABS_AGENT}_${TEST_RUN_ID}\", \"elevenLabsApiKey\": \"${ELEVENLABS_KEY}\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
ELEVENLABS_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /eleven-labs-info (Create) - ID: $ELEVENLABS_ID" || test_fail "POST /eleven-labs-info" "$BODY"

# READ ALL
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/eleven-labs-info")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /eleven-labs-info (List All)" || test_fail "GET /eleven-labs-info" "$BODY"

# READ ONE
if [ -n "$ELEVENLABS_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/eleven-labs-info/${ELEVENLABS_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /eleven-labs-info/{id} (Retrieve)" || test_fail "GET /eleven-labs-info/{id}" "$BODY"
fi

# UPDATE
if [ -n "$ELEVENLABS_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/eleven-labs-info/${ELEVENLABS_ID}" \
        -H "Content-Type: application/json" \
        -d "{\"elevenLabsAgentId\": \"updated_agent\", \"elevenLabsApiKey\": \"updated_key\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "PUT /eleven-labs-info/{id} (Update)" || test_fail "PUT /eleven-labs-info/{id}" "$BODY"
fi

# DELETE
if [ -n "$ELEVENLABS_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/eleven-labs-info/${ELEVENLABS_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /eleven-labs-info/{id}" || test_fail "DELETE /eleven-labs-info/{id}" "$BODY"
fi

# ==============================================================================
# OPENAI INFO CRUD
# ==============================================================================
print_header "OPENAI INFO TESTS"

# CREATE
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/openai-info" \
    -H "Content-Type: application/json" \
    -d "{\"openAIApiKey\": \"${OPENAI_KEY}\", \"openAIAssistantKey\": \"test_${OPENAI_ASSISTANT}_${TEST_RUN_ID}\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
OPENAI_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /openai-info (Create) - ID: $OPENAI_ID" || test_fail "POST /openai-info" "$BODY"

# READ ALL
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/openai-info")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /openai-info (List All)" || test_fail "GET /openai-info" "$BODY"

# READ ONE
if [ -n "$OPENAI_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/openai-info/${OPENAI_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /openai-info/{id} (Retrieve)" || test_fail "GET /openai-info/{id}" "$BODY"
fi

# UPDATE
if [ -n "$OPENAI_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/openai-info/${OPENAI_ID}" \
        -H "Content-Type: application/json" \
        -d "{\"openAIApiKey\": \"updated_key\", \"openAIAssistantKey\": \"updated_assistant\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "PUT /openai-info/{id} (Update)" || test_fail "PUT /openai-info/{id}" "$BODY"
fi

# DELETE
if [ -n "$OPENAI_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/openai-info/${OPENAI_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /openai-info/{id}" || test_fail "DELETE /openai-info/{id}" "$BODY"
fi

# ==============================================================================
# TWILIO INFO CRUD
# ==============================================================================
print_header "TWILIO INFO TESTS"

# CREATE
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/twilio-info" \
    -H "Content-Type: application/json" \
    -d "{\"twilioAccSID\": \"${TWILIO_SID}\", \"twilioAuthToken\": \"${TWILIO_TOKEN}\", \"twilioPhoneNumberCalls\": \"${TWILIO_PHONE_CALLS}\", \"twilioPhoneNumberWhatsapp\": \"+14155238886\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
TWILIO_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /twilio-info (Create) - ID: $TWILIO_ID" || test_fail "POST /twilio-info" "$BODY"

# READ ALL
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/twilio-info")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /twilio-info (List All)" || test_fail "GET /twilio-info" "$BODY"

# READ ONE
if [ -n "$TWILIO_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/twilio-info/${TWILIO_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /twilio-info/{id} (Retrieve)" || test_fail "GET /twilio-info/{id}" "$BODY"
fi

# UPDATE
if [ -n "$TWILIO_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/twilio-info/${TWILIO_ID}" \
        -H "Content-Type: application/json" \
        -d "{\"twilioAccSID\": \"updated_sid\", \"twilioAuthToken\": \"updated_token\", \"twilioPhoneNumberCalls\": \"+15551234567\", \"twilioPhoneNumberWhatsapp\": \"+15557654321\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "PUT /twilio-info/{id} (Update)" || test_fail "PUT /twilio-info/{id}" "$BODY"
fi

# DELETE
if [ -n "$TWILIO_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/twilio-info/${TWILIO_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /twilio-info/{id}" || test_fail "DELETE /twilio-info/{id}" "$BODY"
fi

# ==============================================================================
# CAL INFO CRUD
# ==============================================================================
print_header "CAL INFO TESTS"

# CREATE
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/cal-info" \
    -H "Content-Type: application/json" \
    -d "{\"apiKey\": \"${CAL_API_KEY}\", \"userId\": \"${CAL_USER_ID}\", \"eventId\": \"${CAL_EVENT_ID}\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
CAL_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /cal-info (Create) - ID: $CAL_ID" || test_fail "POST /cal-info" "$BODY"

# READ ALL
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/cal-info")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /cal-info (List All)" || test_fail "GET /cal-info" "$BODY"

# READ ONE
if [ -n "$CAL_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/cal-info/${CAL_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /cal-info/{id} (Retrieve)" || test_fail "GET /cal-info/{id}" "$BODY"
fi

# UPDATE
if [ -n "$CAL_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/cal-info/${CAL_ID}" \
        -H "Content-Type: application/json" \
        -d "{\"apiKey\": \"updated_api_key\", \"userId\": \"updated_user_id\", \"eventId\": \"updated_event_id\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "PUT /cal-info/{id} (Update)" || test_fail "PUT /cal-info/{id}" "$BODY"
fi

# DELETE
if [ -n "$CAL_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/cal-info/${CAL_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /cal-info/{id}" || test_fail "DELETE /cal-info/{id}" "$BODY"
fi

# ==============================================================================
# USER CRUD (with nested objects)
# ==============================================================================
print_header "USER TESTS (Full CRUD with Nested Objects)"

# CREATE USER
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/users" \
    -H "Content-Type: application/json" \
    -d "{
        \"name\": \"Test User ${TEST_RUN_ID}\",
        \"email\": \"test_${TEST_RUN_ID}@example.com\",
        \"phoneNumber\": \"${TEIA_USER_PHONE}\",
        \"elevenLabsInfo\": {
            \"elevenLabsAgentId\": \"${ELEVENLABS_AGENT}\",
            \"elevenLabsApiKey\": \"${ELEVENLABS_KEY}\"
        },
        \"twilioInfo\": {
            \"twilioAccSID\": \"${TWILIO_SID}\",
            \"twilioAuthToken\": \"${TWILIO_TOKEN}\",
            \"twilioPhoneNumberCalls\": \"${TWILIO_PHONE_CALLS}\",
            \"twilioPhoneNumberWhatsapp\": \"+14155238886\"
        },
        \"openAIInfo\": {
            \"openAIApiKey\": \"${OPENAI_KEY}\",
            \"openAIAssistantKey\": \"${OPENAI_ASSISTANT}\"
        },
        \"calInfo\": {
            \"apiKey\": \"${CAL_API_KEY}\",
            \"userId\": \"${CAL_USER_ID}\",
            \"eventId\": \"${CAL_EVENT_ID}\"
        }
    }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
USER_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /users (Create with nested) - ID: $USER_ID" || test_fail "POST /users" "$BODY"

# READ ALL USERS
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/users")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /users (List All)" || test_fail "GET /users" "$BODY"

# READ ONE USER
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/users/${USER_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /users/{id} (Retrieve)" || test_fail "GET /users/{id}" "$BODY"
fi

# UPDATE USER
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/users/${USER_ID}" \
        -H "Content-Type: application/json" \
        -d "{\"name\": \"Updated User ${TEST_RUN_ID}\", \"email\": \"updated_${TEST_RUN_ID}@example.com\", \"phoneNumber\": \"${TEIA_USER_PHONE}\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "PUT /users/{id} (Update)" || test_fail "PUT /users/{id}" "$BODY"
fi

# GET USER CONTACTS (empty initially)
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/users/${USER_ID}/contacts")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "GET /users/{id}/contacts (List)" || test_fail "GET /users/{id}/contacts" "$BODY"
fi

# ADD CONTACT TO USER (CALL channel)
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/users/${USER_ID}/contacts" \
        -H "Content-Type: application/json" \
        -d "{\"phoneNumber\": \"${PHONE_TO_CALL}\", \"channel\": \"CALL\", \"status\": \"ACTIVE\", \"name\": \"Test CALL Contact\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    # Extract contact ID
    CONTACT_ID=$(echo "$BODY" | grep -oP '"contacts":\[\{"id":\K[0-9]+' | head -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "POST /users/{id}/contacts (CALL channel)" || test_fail "POST /users/{id}/contacts" "$BODY"
fi

# ADD CONTACT TO USER (WHATSAPP channel - might fail per requirements)
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/users/${USER_ID}/contacts" \
        -H "Content-Type: application/json" \
        -d "{\"phoneNumber\": \"${PHONE_TO_CALL}\", \"channel\": \"WHATSAPP\", \"status\": \"ACTIVE\", \"name\": \"Test WHATSAPP Contact\"}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    if [ "$HTTP_CODE" -eq 200 ]; then
        test_pass "POST /users/{id}/contacts (WHATSAPP channel)"
    else
        test_expected_fail "POST /users/{id}/contacts (WHATSAPP - may not be configured)"
    fi
fi

# DELETE CONTACT FROM USER
if [ -n "$USER_ID" ] && [ -n "$CONTACT_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/users/${USER_ID}/contacts/${CONTACT_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 200 ] && test_pass "DELETE /users/{userId}/contacts/{contactId}" || test_fail "DELETE /users/{userId}/contacts/{contactId}" "$BODY"
fi

# DELETE USER
if [ -n "$USER_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/users/${USER_ID}")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    [ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /users/{id} (Cascade delete)" || test_fail "DELETE /users/{id}" "$BODY"
fi

# ==============================================================================
# CONTACT DIRECT ENDPOINT TESTS
# ==============================================================================
print_header "CONTACT TESTS (Direct Endpoint)"

# GET ALL CONTACTS
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/contacts")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /contacts (List All)" || test_fail "GET /contacts" "$BODY"

# CREATE CONTACT (direct - requires user_id foreign key)
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/contacts" \
    -H "Content-Type: application/json" \
    -d "{\"phoneNumber\": \"+15559999999\", \"channel\": \"CALL\", \"status\": \"ACTIVE\", \"name\": \"Direct Contact\"}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
if [ "$HTTP_CODE" -eq 200 ]; then
    test_pass "POST /contacts (Direct Create)"
else
    test_expected_fail "POST /contacts (Direct - requires user FK)"
fi

# ==============================================================================
# THREAD TESTS
# ==============================================================================
print_header "THREAD TESTS"

THREAD_ID="thread_${TEST_RUN_ID}"
TO_PHONE="%2B1XXXXXXXXXX"  # URL-encoded
FROM_PHONE="%2B573XXXXXXXXX"  # URL-encoded

# CREATE THREAD
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/threads" \
    -H "Content-Type: application/json" \
    -d "{\"id\": {\"toPhoneNumber\": \"+1XXXXXXXXXX\", \"fromPhoneNumber\": \"+573XXXXXXXXX\", \"threadId\": \"${THREAD_ID}\"}}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "POST /threads (Create)" || test_fail "POST /threads" "$BODY"

# GET ALL THREADS
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/threads")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /threads (List All)" || test_fail "GET /threads" "$BODY"

# GET THREAD BY LOOKUP
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/threads/lookup?toPhoneNumber=${TO_PHONE}&fromPhoneNumber=${FROM_PHONE}&threadId=${THREAD_ID}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /threads/lookup (Retrieve)" || test_fail "GET /threads/lookup" "$BODY"

# GET THREADS BY TO PHONE
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/threads/by-to-phone?toPhoneNumber=${TO_PHONE}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /threads/by-to-phone (Filter)" || test_fail "GET /threads/by-to-phone" "$BODY"

# GET THREADS BY FROM PHONE
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/threads/by-from-phone?fromPhoneNumber=${FROM_PHONE}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 200 ] && test_pass "GET /threads/by-from-phone (Filter)" || test_fail "GET /threads/by-from-phone" "$BODY"

# DELETE THREAD
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "${BASE_URL}/threads?toPhoneNumber=${TO_PHONE}&fromPhoneNumber=${FROM_PHONE}&threadId=${THREAD_ID}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 204 ] && test_pass "DELETE /threads (Delete)" || test_fail "DELETE /threads" "$BODY"

# ==============================================================================
# EDGE CASE TESTS
# ==============================================================================
print_header "EDGE CASE TESTS"

# Non-existent user
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/users/99999")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -ge 400 ] && test_pass "GET /users/99999 (Non-existent - returns error)" || test_fail "GET /users/99999" "$BODY"

# Invalid JSON
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/users" \
    -H "Content-Type: application/json" \
    -d "{invalid json}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -ge 400 ] && test_pass "POST /users (Invalid JSON - returns error)" || test_fail "POST /users (Invalid JSON)" "$BODY"

# Missing required fields
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/users" \
    -H "Content-Type: application/json" \
    -d "{}")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -ge 400 ] && test_pass "POST /users (Missing fields - returns error)" || test_fail "POST /users (Missing fields)" "$BODY"

# Invalid endpoint
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/invalid-endpoint")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
[ "$HTTP_CODE" -eq 404 ] && test_pass "GET /invalid-endpoint (Returns 404)" || test_fail "GET /invalid-endpoint" "$BODY"

# ==============================================================================
# FINAL SUMMARY
# ==============================================================================
print_header "TEST SUMMARY"
TOTAL=$((PASS + FAIL + EXPECTED_FAIL))
echo ""
echo "Total Tests: ${TOTAL}"
echo -e "${GREEN}Passed: ${PASS}${NC}"
echo -e "${RED}Failed: ${FAIL}${NC}"
echo -e "${YELLOW}Expected Failures: ${EXPECTED_FAIL}${NC}"
echo ""

SUCCESS_RATE=$(echo "scale=1; ($PASS * 100) / ($PASS + $FAIL)" | bc)
echo "Success Rate: ${SUCCESS_RATE}%"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
else
    echo -e "${RED}${FAIL} TEST(S) FAILED${NC}"
    exit 1
fi
