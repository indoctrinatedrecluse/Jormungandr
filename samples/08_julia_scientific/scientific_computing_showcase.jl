# ==============================================================================
# Jörmungandr Julia Scientific Computing & High-Performance Analytics Showcase
# ==============================================================================
# Demonstrates Julia REPL, ODE numerical integration, linear algebra, and 
# high-throughput vectorized operations within the Jörmungandr Julia Studio.
#
# Supported in Jörmungandr via:
# - Multi-mode Julia REPL (Julia `julia>`, Package `pkg>`, Shell `shell>`, Help `help?>`)
# - Interactive expression execution (Shift+Enter)
# - Installed environment detection and package status inspector

using Printf

println("="^60)
println(" JÖRMUNGANDR JULIA SCIENTIFIC COMPUTING SUITE")
println("="^60)

# 1. High-Performance Vectorized Array Operations
println("\n[*] 1. Vectorized Matrix Decomposition & Algebra...")
A = [1.0 2.0 3.0; 4.0 5.0 6.0; 7.0 8.0 10.0]
println("Matrix A (3x3):")
display(A)

# Determinant and Trace
det_A = A[1,1]*(A[2,2]*A[3,3] - A[2,3]*A[3,2]) - A[1,2]*(A[2,1]*A[3,3] - A[2,3]*A[3,1]) + A[1,3]*(A[2,1]*A[3,2] - A[2,2]*A[3,1])
tr_A = A[1,1] + A[2,2] + A[3,3]
@printf("    [+] Determinant(A): %.4f\n", det_A)
@printf("    [+] Trace(A):       %.4f\n", tr_A)

# 2. Numerical Integration: 4th-Order Runge-Kutta (RK4) for Lorenz Attractor
println("\n[*] 2. Numerical Integration (Lorenz Attractor via RK4)...")

# Lorenz system differential equations
# dx/dt = sigma*(y - x)
# dy/dt = x*(rho - z) - y
# dz/dt = x*y - beta*z
function lorenz_deriv(state, sigma=10.0, rho=28.0, beta=8.0/3.0)
    x, y, z = state
    dx = sigma * (y - x)
    dy = x * (rho - z) - y
    dz = x * y - beta * z
    return [dx, dy, dz]
end

function rk4_step(deriv, state, dt)
    k1 = deriv(state)
    k2 = deriv(state .+ (dt / 2.0) .* k1)
    k3 = deriv(state .+ (dt / 2.0) .* k2)
    k4 = deriv(state .+ dt .* k3)
    return state .+ (dt / 6.0) .* (k1 .+ 2.0 .* k2 .+ 2.0 .* k3 .+ k4)
end

# Simulate 100 steps
dt = 0.01
state = [1.0, 1.0, 1.0]
trajectory = [state]

for step in 1:100
    global state = rk4_step(lorenz_deriv, state, dt)
    push!(trajectory, state)
end

@printf("    [+] Simulated %d RK4 integration steps.\n", length(trajectory) - 1)
@printf("    [+] Final State: x=%.4f, y=%.4f, z=%.4f\n", state[1], state[2], state[3])

# 3. Monte Carlo Pi Estimation with SIMD Vectorization
println("\n[*] 3. Vectorized Monte Carlo Pi Estimation...")

function estimate_pi(samples::Int)
    inside = 0
    for _ in 1:samples
        x = rand()
        y = rand()
        if x^2 + y^2 <= 1.0
            inside += 1
        end
    end
    return 4.0 * inside / samples
end

pi_est = estimate_pi(500_000)
@printf("    [+] 500,000 iterations estimate: %.6f (Error: %.6f)\n", pi_est, abs(pi_est - pi))

println("\n[OK] Julia scientific computing showcase executed successfully.")
