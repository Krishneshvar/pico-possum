/** Strings for the auth area. */

const auth = {
  login: {
    badges: {
      setup: 'First-Time Setup',
      protected: 'Protected Terminal',
    },
    titles: {
      setup: 'System Administrator Setup',
      signIn: 'Sign In to PicoPOSSUM',
    },
    subtitles: {
      setup:
        "Create your master administrator account to secure your store's database and data.",
      signIn: 'Enter your credentials to unlock terminal sales and operations.',
    },
  },

  fields: {
    administratorName: 'Administrator Name',
    username: 'Username',
    password: 'Password',
    confirmPassword: 'Confirm Password',
  },

  placeholders: {
    administratorName: 'e.g. Store Owner / Manager',
    username: 'admin',
    password: '••••••••',
  },

  password: {
    strengthLabel: 'Password strength',
    levels: {
      weak: 'Weak',
      moderate: 'Moderate',
      good: 'Good',
      strong: 'Strong',
    },
  },

  actions: {
    signIn: 'Sign In',
    createAccount: 'Create Account & Open Terminal',
    authenticating: 'Authenticating...',
    settingUpAccount: 'Setting up account...',
  },

  errors: {
    fullNameRequired: 'Please enter your full name',
    usernameRequired: 'Please choose an administrator username',
    passwordRequired: 'Please enter a secure password',
    passwordTooShort: 'Password must be at least 4 characters long',
    passwordMismatch: 'Passwords do not match',
    credentialsRequired: 'Please enter both username and password',
    invalidCredentials: 'Invalid username or password',
    registrationFailed: 'Failed to complete registration',
  },

  toast: {
    welcomeBack: (name) => `Welcome back, ${name}`,
    signedIn: 'Signed in successfully',
    signedInTitle: 'Signed in',
    signInFailedTitle: 'Sign in failed',
    accountCreated: 'Administrator account created. Terminal unlocked.',
    setupCompleteTitle: 'Setup complete',
    setupFailedTitle: 'Setup failed',
  },

  footer: {
    version: 'v1.0.0',
  },
};

export default auth;
