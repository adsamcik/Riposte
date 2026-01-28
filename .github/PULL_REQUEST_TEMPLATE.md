## Description
<!-- Provide a clear and concise description of the changes -->



## Type of Change
<!-- Mark the relevant option with an `x` -->

- [ ] 🐛 Bug fix (non-breaking change that fixes an issue)
- [ ] ✨ New feature (non-breaking change that adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📝 Documentation update
- [ ] ♻️ Code refactoring
- [ ] ⚡ Performance improvement
- [ ] 🎨 UI/UX improvement
- [ ] 🧪 Test update
- [ ] 🔧 Build/CI configuration change

## Related Issue
<!-- Link to the issue this PR addresses -->

Closes #

## Changes Made
<!-- List the specific changes made in this PR -->

- 
- 
- 

## Testing
<!-- Describe the testing you've done -->

### Test Environment
- [ ] Tested on emulator (Android version: ___)
- [ ] Tested on physical device (Device: ___, Android version: ___)

### Test Cases
<!-- List the test cases you've verified -->

- [ ] 
- [ ] 
- [ ] 

### Screenshots/Videos
<!-- If applicable, add screenshots or videos to demonstrate the changes -->



## Code Quality Checklist
<!-- Ensure all items are checked before submitting -->

### General
- [ ] Code follows project coding conventions (Kotlin style guide)
- [ ] No new compiler warnings introduced
- [ ] Code is self-documenting with clear variable/function names
- [ ] Complex logic has explanatory comments
- [ ] No commented-out code or debug statements

### Architecture & Design
- [ ] Changes follow Clean Architecture principles
- [ ] MVI pattern followed (State/Intent/ViewModel)
- [ ] Proper separation of concerns (Presentation/Domain/Data layers)
- [ ] No feature-to-feature dependencies
- [ ] Dependency injection properly implemented with Hilt

### Android Best Practices
- [ ] No hardcoded strings (uses string resources)
- [ ] Compose best practices followed (stateless composables, hoisted state)
- [ ] Proper lifecycle awareness (collectAsStateWithLifecycle, LaunchedEffect)
- [ ] No memory leaks (proper cleanup in DisposableEffect)
- [ ] Accessibility considered (content descriptions, semantic properties)

### Performance
- [ ] No blocking operations on main thread
- [ ] Efficient database queries (proper indexing, pagination for large datasets)
- [ ] Images loaded efficiently with Coil
- [ ] Compose recomposition optimized (@Stable, @Immutable where appropriate)
- [ ] No unnecessary object allocations in hot paths

### Security
- [ ] No sensitive data hardcoded
- [ ] Proper input validation (especially for FTS queries)
- [ ] File operations use canonical path validation
- [ ] No cleartext HTTP traffic
- [ ] User data handled securely

### Testing
- [ ] Unit tests written/updated for ViewModels and use cases
- [ ] Repository tests cover happy and error paths
- [ ] UI tests added for critical user flows (if applicable)
- [ ] All tests pass locally (`./gradlew test`)
- [ ] Test coverage is adequate (aim for >80% for business logic)

### Code Quality Tools
- [ ] ktlint formatting passes (`./gradlew ktlintFormat`)
- [ ] Detekt static analysis passes (`./gradlew detekt`)
- [ ] Android Lint passes with no new errors (`./gradlew lintDebug`)
- [ ] No detekt baseline changes (or justified if necessary)

### Documentation
- [ ] Public APIs documented with KDoc
- [ ] README updated if needed
- [ ] Architecture documentation updated if needed
- [ ] CHANGELOG.md updated

### Database Changes
<!-- If this PR includes database changes -->
- [ ] Room migration strategy implemented (if schema changed)
- [ ] Migration tested (upgrade and downgrade if applicable)
- [ ] Database version incremented
- [ ] FTS tables updated properly (if applicable)

### Build & Dependencies
- [ ] No new dependencies added (or justified if necessary)
- [ ] Gradle build files follow conventions
- [ ] Version catalog used for dependencies
- [ ] No version conflicts introduced
- [ ] Build time not significantly increased

## Breaking Changes
<!-- List any breaking changes and migration steps -->

- None

OR

- **Change**: 
  **Migration**: 
  **Impact**: 

## Performance Impact
<!-- Describe any performance implications -->

- [ ] No measurable performance impact
- [ ] Performance improved: 
- [ ] Performance impact expected: 

## Reviewer Notes
<!-- Any specific areas you'd like reviewers to focus on -->



## Pre-Submit Checklist
<!-- Final checks before submitting -->

- [ ] I have performed a self-review of my code
- [ ] I have tested my changes thoroughly
- [ ] I have updated documentation as needed
- [ ] I have added tests that prove my fix/feature works
- [ ] All new and existing tests pass
- [ ] My code generates no new warnings
- [ ] I have checked for accessibility issues
- [ ] I have considered security implications

## Additional Context
<!-- Add any other context about the PR here -->


