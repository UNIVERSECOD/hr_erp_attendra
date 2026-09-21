-- Fix admin password hash to use BCrypt cost factor 12 with a secure random salt
-- The original V004 hash was not a valid BCrypt hash for 'admin123'
UPDATE users
SET password_hash = '$2a$12$tsWFUh9Wahb8CFGeVCIR.uSHAQomV.a4Gjfef3WK/UT.XznSvMUQ6'
WHERE username = 'admin';
