import Link from "next/link";

export default function SourceRefs(props) {
    const source = props.source
    if (source.length === 0) {
        return <></>
    }

    return <div className={props.span ? "source-span" : "source"}>{props.span ? " (source: " : "Source:"}{' '}
        {source.map((s, index) => {
            const prefix = index > 0 ? ", " : ""
            if (s.pubId !== null) {
                return <>{prefix}<Link href={`/publication/${s.pubId}`}>{s.pubRefId}</Link>{":" + s.refText}</>
            }
            else if (s.refText.startsWith("http")) {
                return <>{prefix}<a href={s.refText}>{s.refText}</a></>
            }
            else {
                return prefix + s.refText
            }
        })}
        {props.span && ")"}
    </div>
}
