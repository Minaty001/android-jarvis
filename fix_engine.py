with open('app/src/main/java/com/jarvis/execution/VerificationEngine.kt', 'r') as f:
    lines = f.readlines()
with open('app/src/main/java/com/jarvis/execution/VerificationEngine.kt', 'w') as f:
    for line in lines:
        if line.strip() == "}": # wait this is risky
            pass
