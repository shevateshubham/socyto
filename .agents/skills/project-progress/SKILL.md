---
name: project-progress
description: Implement SOCYTO configurable binary and quantity-based task progress with relative effort weighting and explicit publication.
---
# Project Progress Skill
1. Read REQUIREMENTS.md and ARCHITECTURE.md.
2. Keep each project's task set custom, not room-wise fixed.
3. Compute task fraction as binary 0/1 or clamp(completed/required,0,1); reject nonpositive required quantity.
4. Effort weights must be positive; defaults are small=1, medium=2, large=3.
5. Overall = 100 * sum(weight * fraction) / sum(weights); empty denominator yields no-progress state.
6. Admin draft and client-facing published snapshot must be distinct; never auto-publish.
7. Test boundaries, partial quantity, mixed weights, empty projects and post-edit pre-publish visibility.
