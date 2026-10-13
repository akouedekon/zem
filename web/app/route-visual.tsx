export default function RouteVisual() {
  return <div className="route-visual" aria-label="Illustration d’un trajet ZEM, sans données en temps réel">
    <div className="visual-top"><span>Cotonou · Bénin</span><span className="visual-pill">Mobilité de proximité</span></div>
    <svg viewBox="0 0 520 330" role="img" aria-label="Trajet illustratif entre Étoile Rouge et Dantokpa">
      <defs><pattern id="streets" width="65" height="65" patternUnits="userSpaceOnUse"><path d="M0 20H65M22 0V65" fill="none" stroke="#fff" strokeWidth="12" /></pattern></defs>
      <rect width="520" height="330" fill="#e3ede7" /><rect width="520" height="330" fill="url(#streets)" />
      <path d="M360 0Q310 90 415 190T450 330H520V0" fill="#bfdfe2" />
      <path d="M100 246V150H230V85H335" fill="none" stroke="#fff" strokeWidth="15" strokeLinejoin="round" />
      <path d="M100 246V150H230V85H335" fill="none" stroke="#087553" strokeWidth="7" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx="100" cy="246" r="12" fill="#087553" stroke="white" strokeWidth="5" /><circle cx="335" cy="85" r="12" fill="#e6aa36" stroke="white" strokeWidth="5" />
      <rect x="42" y="272" width="128" height="32" rx="12" fill="white" /><text x="58" y="293" fill="#18372e" fontSize="14">Étoile Rouge</text>
      <rect x="272" y="32" width="110" height="32" rx="12" fill="white" /><text x="289" y="53" fill="#18372e" fontSize="14">Dantokpa</text>
      <g transform="translate(230 145)"><circle r="25" fill="#087553" /><text textAnchor="middle" y="7" fontSize="22" fill="white">Z</text></g>
    </svg>
    <div className="visual-bottom"><span className="visual-icon">↗</span><div><strong>Un trajet. Une activité reconnue.</strong><small>Conducteurs habilités · recettes traçables</small></div></div>
    <p className="visual-caption">Illustration du service · ce visuel n’est pas une carte de suivi.</p>
  </div>;
}
