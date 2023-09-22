import Link from "next/link";

export default function SourceRefs(props) {
    const source = props.source
    if (source.length === 0) {
        return <></>
    }

    return <div className="source">Source:{' '}
        {source.map(s => {
            if (s.pubId !== null) {
                return <><Link href={`/publication/${s.pubId}`}>{s.pubRefId}</Link>{":" + s.refText}</>
            }
            else if (s.refText.startsWith("http")) {
                return <a href={s.refText}>{s.refText}</a>
            }
            else {
                return s.refText
            }
        })}
    </div>
}
