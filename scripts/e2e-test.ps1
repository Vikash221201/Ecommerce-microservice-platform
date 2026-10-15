# ==============================================================================
# End-to-End Microservices Platform Automated Verification Script
# ==============================================================================
# Simulates full customer and administrator journeys through the API Gateway:
# 1. Healthcheck verification
# 2. Customer registration & authentication
# 3. Admin authentication
# 4. Admin product catalog provisioning
# 5. Customer catalog browse & search
# 6. Customer cart management (add, inspect, clear)
# 7. Customer order checkout
# 8. Order persistence & status retrieval
# 9. Admin order lifecycle transition
# 10. Security boundary & authorization negative tests (401 / 403)
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0

function Report-Step {
    param(
        [string]$StepName,
        [bool]$Success,
        [string]$Details = ""
    )
    if ($Success) {
        Write-Host "  [PASS] $StepName" -ForegroundColor Green
        if ($Details) { Write-Host "         $Details" -ForegroundColor DarkGray }
        $script:passed++
    } else {
        Write-Host "  [FAIL] $StepName" -ForegroundColor Red
        if ($Details) { Write-Host "         $Details" -ForegroundColor Yellow }
        $script:failed++
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   E-COMMERCE MICROSERVICES PLATFORM - END-TO-END TEST" -ForegroundColor Cyan
Write-Host "   Target Gateway: $GatewayUrl" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$uniqueSuffix = [System.Guid]::NewGuid().ToString().Substring(0, 8)
$customerEmail = "cust_$uniqueSuffix@example.com"
$customerPass = "SecureCustomer123!"
$adminEmail = "admin_$uniqueSuffix@ecommerce.com"
$adminPass = "SuperAdmin123!"

# ------------------------------------------------------------------------------
# 1. Gateway Healthcheck
# ------------------------------------------------------------------------------
Write-Host "1. Testing Gateway Health Endpoint..." -ForegroundColor Yellow
try {
    $res = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 10
    $isUp = ($res.status -eq "UP")
    Report-Step "Gateway /actuator/health is UP" $isUp "Status: $($res.status)"
} catch {
    Report-Step "Gateway /actuator/health accessible" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 2. Customer Registration
# ------------------------------------------------------------------------------
Write-Host "`n2. Customer Registration & Login..." -ForegroundColor Yellow
$customerToken = $null
$customerId = $null

try {
    $regBody = @{
        fullName = "Jane Customer $uniqueSuffix"
        email = $customerEmail
        password = $customerPass
        role = "ROLE_CUSTOMER"
    } | ConvertTo-Json

    $regRes = Invoke-RestMethod -Uri "$GatewayUrl/api/auth/register" -Method Post -Body $regBody -ContentType "application/json"
    $customerToken = $regRes.token
    $customerId = $regRes.userId
    Report-Step "Customer Registration" ($null -ne $customerToken) "User ID: $customerId | Email: $customerEmail"
} catch {
    Report-Step "Customer Registration" $false $_.Exception.Message
}

# If registration succeeded, test explicit login
if ($customerToken) {
    try {
        $loginBody = @{
            email = $customerEmail
            password = $customerPass
        } | ConvertTo-Json

        $loginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
        $customerToken = $loginRes.token
        Report-Step "Customer Login with Credentials" ($null -ne $customerToken) "JWT Token issued successfully"
    } catch {
        Report-Step "Customer Login with Credentials" $false $_.Exception.Message
    }
}

# ------------------------------------------------------------------------------
# 3. Admin Registration & Login
# ------------------------------------------------------------------------------
Write-Host "`n3. Administrator Provisioning..." -ForegroundColor Yellow
$adminToken = $null

try {
    $adminRegBody = @{
        fullName = "SysAdmin $uniqueSuffix"
        email = $adminEmail
        password = $adminPass
        role = "ROLE_ADMIN"
    } | ConvertTo-Json

    $adminRes = Invoke-RestMethod -Uri "$GatewayUrl/api/auth/register" -Method Post -Body $adminRegBody -ContentType "application/json"
    $adminToken = $adminRes.token
    Report-Step "Admin Registration (ROLE_ADMIN)" ($null -ne $adminToken) "Admin Email: $adminEmail"
} catch {
    Report-Step "Admin Registration (ROLE_ADMIN)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 4. Catalog Management (Admin Only)
# ------------------------------------------------------------------------------
Write-Host "`n4. Admin Product Creation in Catalog..." -ForegroundColor Yellow
$createdProductId = $null

if ($adminToken) {
    try {
        $prodBody = @{
            name = "Sony WH-1000XM5 $uniqueSuffix"
            description = "Noise cancelling wireless over-ear headphones"
            category = "Audio"
            price = 399.99
            stockQuantity = 50
            imageUrl = "https://images.example.com/sony-xm5.png"
        } | ConvertTo-Json

        $adminHeaders = @{
            Authorization = "Bearer $adminToken"
        }

        $prodRes = Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Method Post -Body $prodBody -Headers $adminHeaders -ContentType "application/json"
        $createdProductId = $prodRes.id
        Report-Step "Admin creates product" ($null -ne $createdProductId) "Product ID: $createdProductId | Name: $($prodRes.name)"
    } catch {
        Report-Step "Admin creates product" $false $_.Exception.Message
    }
} else {
    Report-Step "Admin creates product" $false "Skipped: No admin token"
}

# ------------------------------------------------------------------------------
# 5. Customer Catalog Browse & Search
# ------------------------------------------------------------------------------
Write-Host "`n5. Customer Catalog Browse & Search..." -ForegroundColor Yellow
try {
    $allProducts = Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Method Get
    $found = ($allProducts | Where-Object { $_.id -eq $createdProductId })
    Report-Step "Customer browses full catalog" ($null -ne $found) "Catalog contains $(@($allProducts).Count) items"
} catch {
    Report-Step "Customer browses full catalog" $false $_.Exception.Message
}

try {
    $searchResults = Invoke-RestMethod -Uri "$GatewayUrl/api/products/search?q=Sony" -Method Get
    $searchFound = ($searchResults | Where-Object { $_.id -eq $createdProductId })
    Report-Step "Customer searches catalog by keyword 'Sony'" ($null -ne $searchFound) "Search returned $(@($searchResults).Count) match(es)"
} catch {
    Report-Step "Customer searches catalog by keyword 'Sony'" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# 6. Customer Cart Lifecycle
# ------------------------------------------------------------------------------
Write-Host "`n6. Customer Cart Management..." -ForegroundColor Yellow
$cartTargetId = if ($customerId) { $customerId } else { $customerEmail }

if ($customerToken -and $createdProductId) {
    $custHeaders = @{
        Authorization = "Bearer $customerToken"
    }

    try {
        $cartItemBody = @{
            productId = $createdProductId
            productName = "Sony WH-1000XM5 $uniqueSuffix"
            price = 399.99
            quantity = 2
        } | ConvertTo-Json

        $cartRes = Invoke-RestMethod -Uri "$GatewayUrl/api/cart/$cartTargetId/items" -Method Post -Body $cartItemBody -Headers $custHeaders -ContentType "application/json"
        $itemAdded = ($cartRes.items | Where-Object { $_.productId -eq $createdProductId })
        Report-Step "Customer adds item to cart" ($null -ne $itemAdded) "Cart item count: $(@($cartRes.items).Count)"
    } catch {
        Report-Step "Customer adds item to cart" $false $_.Exception.Message
    }

    try {
        $getCartRes = Invoke-RestMethod -Uri "$GatewayUrl/api/cart/$cartTargetId" -Method Get -Headers $custHeaders
        $cartMatches = ($getCartRes.userId -eq $cartTargetId -and $getCartRes.items.Count -gt 0)
        Report-Step "Customer inspects cart" $cartMatches "Retrieved cart for user: $($getCartRes.userId)"
    } catch {
        Report-Step "Customer inspects cart" $false $_.Exception.Message
    }
} else {
    Report-Step "Customer cart operations" $false "Skipped: Missing token or productId"
}

# ------------------------------------------------------------------------------
# 7. Customer Checkout & Order Creation
# ------------------------------------------------------------------------------
Write-Host "`n7. Order Creation & Checkout..." -ForegroundColor Yellow
$createdOrderId = $null

if ($customerToken -and $createdProductId) {
    try {
        $orderBody = @{
            userId = $cartTargetId
            items = @(
                @{
                    productId = $createdProductId
                    productName = "Sony WH-1000XM5 $uniqueSuffix"
                    price = 399.99
                    quantity = 2
                }
            )
        } | ConvertTo-Json

        $orderRes = Invoke-RestMethod -Uri "$GatewayUrl/api/orders" -Method Post -Body $orderBody -Headers $custHeaders -ContentType "application/json"
        $createdOrderId = $orderRes.id
        $isPending = ($orderRes.status -eq "PENDING")
        Report-Step "Customer places order (Total: $799.98)" ($null -ne $createdOrderId -and $isPending) "Order ID: $createdOrderId | Status: $($orderRes.status)"
    } catch {
        Report-Step "Customer places order" $false $_.Exception.Message
    }

    if ($createdOrderId) {
        try {
            $fetchOrder = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$createdOrderId" -Method Get -Headers $custHeaders
            $orderValid = ($fetchOrder.id -eq $createdOrderId -and $fetchOrder.status -eq "PENDING")
            Report-Step "Customer inspects order by ID" $orderValid "Order Total: $($fetchOrder.totalAmount)"
        } catch {
            Report-Step "Customer inspects order by ID" $false $_.Exception.Message
        }
    }
} else {
    Report-Step "Order creation" $false "Skipped: Missing dependencies"
}

# ------------------------------------------------------------------------------
# 8. Customer Payment Processing & Lifecycle
# ------------------------------------------------------------------------------
Write-Host "`n8. Customer Payment Processing & Lifecycle..." -ForegroundColor Yellow
$createdPaymentId = $null

if ($customerToken -and $createdOrderId) {
    # 8.1 Create payment
    try {
        $payBody = @{
            orderId = $createdOrderId
            userId = $cartTargetId
            amount = 799.98
            paymentMethod = "TEST_SUCCESS"
        } | ConvertTo-Json

        $payRes = Invoke-RestMethod -Uri "$GatewayUrl/api/payments" -Method Post -Body $payBody -Headers $custHeaders -ContentType "application/json"
        $createdPaymentId = $payRes.id
        $isSuccess = ($payRes.status -eq "SUCCESS" -and $payRes.orderId -eq $createdOrderId -and $null -ne $payRes.transactionReference)
        Report-Step "Customer initiates payment ($799.98) -> SUCCESS" $isSuccess "Payment ID: $createdPaymentId | Ref: $($payRes.transactionReference)"
    } catch {
        Report-Step "Customer initiates payment" $false $_.Exception.Message
    }

    # 8.2 Inspect payment by ID
    if ($createdPaymentId) {
        try {
            $fetchPay = Invoke-RestMethod -Uri "$GatewayUrl/api/payments/$createdPaymentId" -Method Get -Headers $custHeaders
            $validPay = ($fetchPay.id -eq $createdPaymentId -and $fetchPay.status -eq "SUCCESS")
            Report-Step "Customer retrieves payment by paymentId" $validPay "Status: $($fetchPay.status)"
        } catch {
            Report-Step "Customer retrieves payment by paymentId" $false $_.Exception.Message
        }

        # 8.3 Inspect payment by Order ID
        try {
            $fetchPayByOrder = Invoke-RestMethod -Uri "$GatewayUrl/api/payments/order/$createdOrderId" -Method Get -Headers $custHeaders
            $validOrderByPay = ($fetchPayByOrder.id -eq $createdPaymentId -and $fetchPayByOrder.orderId -eq $createdOrderId)
            Report-Step "Customer retrieves payment by orderId" $validOrderByPay "Order ID: $createdOrderId"
        } catch {
            Report-Step "Customer retrieves payment by orderId" $false $_.Exception.Message
        }

        # 8.4 Duplicate payment rejection (409 Conflict)
        try {
            $dupBody = @{
                orderId = $createdOrderId
                userId = $cartTargetId
                amount = 799.98
                paymentMethod = "TEST_SUCCESS"
            } | ConvertTo-Json
            Invoke-RestMethod -Uri "$GatewayUrl/api/payments" -Method Post -Body $dupBody -Headers $custHeaders -ContentType "application/json"
            Report-Step "Duplicate payment rejected with 409" $false "Unexpectedly allowed duplicate payment"
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            $is409 = ($statusCode -eq 409)
            Report-Step "Duplicate payment rejected with 409 Conflict" $is409 "HTTP Status: $statusCode"
        }

        # 8.5 Refund payment
        try {
            $refundRes = Invoke-RestMethod -Uri "$GatewayUrl/api/payments/$createdPaymentId/refund" -Method Post -Headers $custHeaders
            $isRefunded = ($refundRes.status -eq "REFUNDED")
            Report-Step "Customer refunds payment -> REFUNDED" $isRefunded "New Status: $($refundRes.status)"
        } catch {
            Report-Step "Customer refunds payment" $false $_.Exception.Message
        }

        # 8.6 Repeated refund rejection (400 Bad Request)
        try {
            Invoke-RestMethod -Uri "$GatewayUrl/api/payments/$createdPaymentId/refund" -Method Post -Headers $custHeaders
            Report-Step "Repeated refund rejected with 400" $false "Unexpectedly allowed duplicate refund"
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            $is400 = ($statusCode -eq 400)
            Report-Step "Repeated refund rejected with 400 Bad Request" $is400 "HTTP Status: $statusCode"
        }
    }
} else {
    Report-Step "Customer payment processing" $false "Skipped: Missing orderId or customer token"
}

# ------------------------------------------------------------------------------
# 9. Admin Order Lifecycle Transition
# ------------------------------------------------------------------------------
Write-Host "`n9. Admin Order Status Transition..." -ForegroundColor Yellow
if ($adminToken -and $createdOrderId) {
    try {
        $updateRes = Invoke-RestMethod -Uri "$GatewayUrl/api/orders/$createdOrderId/status?status=CONFIRMED" -Method Put -Headers $adminHeaders
        $isConfirmed = ($updateRes.status -eq "CONFIRMED")
        Report-Step "Admin updates order status to CONFIRMED" $isConfirmed "Order ID: $createdOrderId | New Status: $($updateRes.status)"
    } catch {
        Report-Step "Admin updates order status to CONFIRMED" $false $_.Exception.Message
    }
} else {
    Report-Step "Admin updates order status" $false "Skipped: Missing admin token or orderId"
}

# ------------------------------------------------------------------------------
# 10. Clean up Customer Cart
# ------------------------------------------------------------------------------
Write-Host "`n10. Customer Cart Cleanup..." -ForegroundColor Yellow
if ($customerToken) {
    try {
        Invoke-RestMethod -Uri "$GatewayUrl/api/cart/$cartTargetId" -Method Delete -Headers $custHeaders
        Report-Step "Customer clears cart after checkout" $true "Cart successfully emptied"
    } catch {
        Report-Step "Customer clears cart after checkout" $false $_.Exception.Message
    }
}

# ------------------------------------------------------------------------------
# 11. Negative Security Boundary Checks
# ------------------------------------------------------------------------------
Write-Host "`n11. Security Boundary & Authorization Checks..." -ForegroundColor Yellow

# Negative 1: Customer cannot create products (403 Forbidden)
try {
    $unauthProduct = @{
        name = "Hacker Product"
        price = 1.00
    } | ConvertTo-Json

    Invoke-RestMethod -Uri "$GatewayUrl/api/products" -Method Post -Body $unauthProduct -Headers $custHeaders -ContentType "application/json"
    Report-Step "ROLE_CUSTOMER product creation rejected with 403" $false "Unexpectedly allowed customer to create product"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $is403 = ($statusCode -eq 403)
    Report-Step "ROLE_CUSTOMER product creation rejected with 403" $is403 "HTTP Status: $statusCode"
}

# Negative 2: Unauthenticated request to protected endpoint (401 Unauthorized)
try {
    Invoke-RestMethod -Uri "$GatewayUrl/api/cart/user-secret-999" -Method Get
    Report-Step "Unauthenticated access rejected with 401" $false "Unexpectedly allowed unauthenticated access"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $is401 = ($statusCode -eq 401)
    Report-Step "Unauthenticated access rejected with 401" $is401 "HTTP Status: $statusCode"
}

# Negative 3: Customer accessing another customer's cart (403 Forbidden)
try {
    Invoke-RestMethod -Uri "$GatewayUrl/api/cart/other-user-id-9999" -Method Get -Headers $custHeaders
    Report-Step "Cross-user cart access rejected with 403" $false "Unexpectedly allowed accessing another user's cart"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $is403 = ($statusCode -eq 403)
    Report-Step "Cross-user cart access rejected with 403" $is403 "HTTP Status: $statusCode"
}

# Negative 4: Unauthenticated payment initiation (401 Unauthorized)
try {
    $rawPayBody = @{
        orderId = [System.Guid]::NewGuid().ToString()
        userId = "any-user"
        amount = 50.00
        paymentMethod = "TEST_SUCCESS"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$GatewayUrl/api/payments" -Method Post -Body $rawPayBody -ContentType "application/json"
    Report-Step "Unauthenticated payment access rejected with 401" $false "Unexpectedly allowed unauthenticated payment"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $is401 = ($statusCode -eq 401)
    Report-Step "Unauthenticated payment access rejected with 401" $is401 "HTTP Status: $statusCode"
}

# ------------------------------------------------------------------------------
# Scorecard
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   TEST SUITE EXECUTION SCORECARD" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  PASSED: $passed" -ForegroundColor Green
Write-Host "  FAILED: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "  TOTAL : $($passed + $failed)" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan

if ($failed -gt 0) {
    exit 1
} else {
    exit 0
}
